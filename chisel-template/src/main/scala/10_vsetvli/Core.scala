package vsetvli

import chisel3._
import chisel3.util._
import common.Consts._
import common.Instructions._ 

class Core extends Module {
  val io = IO(new Bundle {
    val imem = Flipped(new ImemPortIo())
    val dmem = Flipped(new DmemPortIo())
    val exit = Output(Bool())
    val gp = Output(UInt(WORD_LEN.W)) // global pointer
    val pc = Output(UInt(WORD_LEN.W))
  })

  // generate 32bit x 32 register
  // WORD_LEN = 32 defined in Consts.scala
  val regfile = Mem(32, UInt(WORD_LEN.W))
  val csr_regfile = Mem(4096, UInt(WORD_LEN.W))
  val vec_regfile = Mem(32, UInt(WORD_LEN.W))

  //**************************
  // Instruction Fetch Stage
  // Load instructions from Memory

  // generate PC register initialized 0
  val pc_reg = RegInit(START_ADDR)

  // connect pc_reg to output_port_addr
  io.imem.addr := pc_reg
  val inst = io.imem.inst

  // setup branch and jump
  // br_flg, br_target are set in EX Stage
  val pc_plus4 = pc_reg + 4.U(WORD_LEN.W)
  val br_flg = Wire(Bool())
  val br_target = Wire(UInt(WORD_LEN.W))
  val jmp_flg = (inst === JAL || inst === JALR)
  val alu_out = Wire(UInt(WORD_LEN.W))
  
  val pc_next = MuxCase(pc_plus4, Seq(
    br_flg -> br_target,
    jmp_flg -> alu_out,
    (inst === ECALL) -> csr_regfile(0x305)
  ))
  pc_reg := pc_next

  //*************************
  // Instruction Decode Stage
  // p25       (wb <-> rd ?)
  // get register_number
  val rs1_addr = inst(19, 15)
  val rs2_addr = inst(24, 20)
  val wb_addr = inst(11, 7)
  // read register_data,    Mux: Multiplexer
  val rs1_data = Mux((rs1_addr =/= 0.U(WORD_LEN.U)), regfile(rs1_addr), 0.U(WORD_LEN.W))
  val rs2_data = Mux((rs2_addr =/= 0.U(WORD_LEN.U)), regfile(rs2_addr), 0.U(WORD_LEN.W))
  // Sign Extension (of offset ?) for I-type Instruction
  val imm_i = inst(31, 20)
  val imm_i_sext = Cat(Fill(20, imm_i(11)), imm_i)
  // Sign Extension for S-type Instruction
  val imm_s = Cat(inst(31, 25), inst(11, 7))
  val imm_s_sext = Cat(Fill(20, imm_s(11)), imm_s)
  // Sign Extension for B-type Instruction
  val imm_b = Cat(inst(31), inst(7), inst(30,25), inst(11, 8))
  val imm_b_sext = Cat(Fill(19, imm_b(11)), imm_b, 0.U(1.U))
  // Sign Extension for J-type Instruction
  val imm_j = Cat(inst(31), inst(19, 12), inst(20), inst(30, 21))
  val imm_j_sext = Cat(Fill(11, imm_j(19)), imm_j, 0.U(1.U))
  // U-type
  val imm_u = inst(31, 12)
  val imm_u_shifted = Cat(imm_u, Fill(12, 0.U))
  // Z-type
  val imm_z = inst(19, 15)
  val imm_z_uext = Cat(Fill(27, 0.U), imm_z)
  // Decode stage upgrade
  //p54-55
  val csignals = ListLookup(inst,
  List(ALU_X, OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X, CSR_X),
    Array(
      LW    -> List(ALU_ADD  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_MEM, CSR_X),
      SW    -> List(ALU_ADD  , OP1_RS1, OP2_IMS, MEN_S, REN_X, WB_X  , CSR_X),
      ADD   -> List(ALU_ADD  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, CSR_X),
      ADDI  -> List(ALU_ADD  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, CSR_X),
      SUB   -> List(ALU_SUB  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, CSR_X),
      AND   -> List(ALU_AND  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, CSR_X),
      OR    -> List(ALU_OR   , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, CSR_X),
      XOR   -> List(ALU_XOR  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, CSR_X),
      ANDI  -> List(ALU_AND  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, CSR_X),
      ORI   -> List(ALU_OR   , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, CSR_X),
      XORI  -> List(ALU_XOR  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, CSR_X),
      SLL   -> List(ALU_SLL  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, CSR_X),
      SRL   -> List(ALU_SRL  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, CSR_X),
      SRA   -> List(ALU_SRA  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, CSR_X),
      SLLI  -> List(ALU_SLL  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, CSR_X),
      SRLI  -> List(ALU_SRL  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, CSR_X),
      SRAI  -> List(ALU_SRA  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, CSR_X),
      SLT   -> List(ALU_SLT  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, CSR_X),
      SLTU  -> List(ALU_SLTU , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, CSR_X),
      SLTI  -> List(ALU_SLT  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, CSR_X),
      SLTIU -> List(ALU_SLTU , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, CSR_X),
      BEQ   -> List(BR_BEQ   , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  , CSR_X),
      BNE   -> List(BR_BNE   , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  , CSR_X),
      BGE   -> List(BR_BGE   , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  , CSR_X),
      BGEU  -> List(BR_BGEU  , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  , CSR_X),
      BLT   -> List(BR_BLT   , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  , CSR_X),
      BLTU  -> List(BR_BLTU  , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  , CSR_X),
      JAL   -> List(ALU_ADD  , OP1_PC , OP2_IMJ, MEN_X, REN_S, WB_PC , CSR_X),
      JALR  -> List(ALU_JALR , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_PC , CSR_X),
      LUI   -> List(ALU_ADD  , OP1_X  , OP2_IMU, MEN_X, REN_S, WB_ALU, CSR_X),
      AUIPC -> List(ALU_ADD  , OP1_PC , OP2_IMU, MEN_X, REN_S, WB_ALU, CSR_X),
      CSRRW -> List(ALU_COPY1, OP1_RS1, OP2_X  , MEN_X, REN_S, WB_CSR, CSR_W),
      CSRRWI-> List(ALU_COPY1, OP1_IMZ, OP2_X  , MEN_X, REN_S, WB_CSR, CSR_W),
      CSRRS -> List(ALU_COPY1, OP1_RS1, OP2_X  , MEN_X, REN_S, WB_CSR, CSR_S),
      CSRRSI-> List(ALU_COPY1, OP1_IMZ, OP2_X  , MEN_X, REN_S, WB_CSR, CSR_S),
      CSRRC -> List(ALU_COPY1, OP1_RS1, OP2_X  , MEN_X, REN_S, WB_CSR, CSR_C),
      CSRRCI-> List(ALU_COPY1, OP1_IMZ, OP2_X  , MEN_X, REN_S, WB_CSR, CSR_C),
      ECALL -> List(ALU_X    , OP1_X  , OP2_X  , MEN_X, REN_X, WB_X  , CSR_E),
      VSETVLI->List(ALU_X    , OP1_X  , OP2_X  , MEN_X, REN_S, WB_VL , CSR_V)
    ))
  val exe_fun :: op1_sel :: op2_sel :: mem_wen :: rf_wen :: wb_sel :: csr_cmd :: Nil = csignals
  // identify instruction-type(R,S,I....) for Execute Stage
  val op1_data = MuxCase(0.U(WORD_LEN.W), Seq(
    (op1_sel === OP1_RS1) -> rs1_data,
    (op1_sel === OP1_PC ) -> pc_reg,
    (op1_sel === OP1_IMZ) -> imm_z_uext
  ))
  val op2_data = MuxCase(0.U(WORD_LEN.W), Seq(
    (op2_sel === OP2_RS2) -> rs2_data,
    (op2_sel === OP2_IMI) -> imm_i_sext,
    (op2_sel === OP2_IMS) -> imm_s_sext,
    (op2_sel === OP2_IMJ) -> imm_j_sext,
    (op2_sel === OP2_IMU) -> imm_u_shifted
  ))

  //***************************
  // Execute Stage
  alu_out := MuxCase(0.U(WORD_LEN.W), Seq(
    (exe_fun === ALU_ADD) -> (op1_data + op2_data),
    (exe_fun === ALU_SUB) -> (op1_data - op2_data),
    (exe_fun === ALU_AND) -> (op1_data & op2_data),
    (exe_fun === ALU_OR) -> (op1_data | op2_data),
    (exe_fun === ALU_XOR) -> (op1_data ^ op2_data),
    (exe_fun === ALU_SLL) -> (op1_data << op2_data(4, 0))(31, 0),
    (exe_fun === ALU_SRL) -> (op1_data >> op2_data(4, 0)).asUInt(),
    (exe_fun === ALU_SRA) -> (op1_data.asUInt() >> op2_data(4, 0)).asUInt(),
    (exe_fun === ALU_SLT) -> (op1_data.asSInt() < op2_data.asSInt()).asUInt(),
    (exe_fun === ALU_SLTU) -> (op1_data < op2_data).asUInt(),
    (exe_fun === ALU_JALR) -> ((op1_data + op2_data) & ~1.U(WORD_LEN.W)),
    (exe_fun === ALU_COPY1) -> op1_data
  ))

  // for branch
  br_flg := MuxCase(false.B, Seq(
    (exe_fun === BR_BEQ) -> (op1_data === op2_data),
    (exe_fun === BR_BNE) -> !(op1_data === op2_data),
    (exe_fun === BR_BLT) -> (op1_data.asSInt() < op2_data.asSInt()),
    (exe_fun === BR_BGE) -> !(op1_data.asSInt() < op2_data.asSInt()),
    (exe_fun === BR_BLTU) -> (op1_data < op2_data),
    (exe_fun === BR_BGEU) -> !(op1_data < op2_data)
  ))
  br_target := pc_reg + imm_b_sext

  //****************************
  // Memory Access Stage
  io.dmem.addr := alu_out
  //io.dmem.wen := Mux(mem_wen === MEN_S, 1.U(MEN_LEN.W), 0.U(MEN_LEN.W))
  io.dmem.wen := mem_wen
  io.dmem.wdata := rs2_data

  // for CSR execution
  val csr_addr = Mux(csr_cmd === CSR_E, 0x342.U(CSR_ADDR_LEN.W), inst(31, 20))
  // read CSR
  val csr_rdata = csr_regfile(csr_addr)
  // write CSR
  val csr_wdata = MuxCase(0.U(WORD_LEN.W), Seq(
    (csr_cmd === CSR_W) -> op1_data,
    (csr_cmd === CSR_S) -> (csr_rdata | op1_data),
    (csr_cmd === CSR_C) -> (csr_rdata & ~op1_data),
    (csr_cmd === CSR_E) -> 11.U(WORD_LEN.W) // WBdata of ECALL is 8 + prv mode(=3:machine mode)
  ))
  // CSR case
  when(csr_cmd > 0.U){
    csr_regfile(csr_addr) := csr_wdata
  }

  // VSETVLI
  val vtype = imm_i_sext
  val vsew = vtype(4, 2)
  val vlmul = vtype(1, 0)
  val vlmax = ((VLEN.U << vlmul) >> (vsew + 3.U(3.W))).asUInt() // VLMAX = VLEN * LMUL / SEW.  LMUL = 2^{vlmul}, SEW = 2^{vsew+3} (p232)

  val avl = rs1_data
  val vl = MuxCase(0.U(WORD_LEN.W), Seq(
    (avl <= vlmax) -> avl,
    (avl > vlmax) -> vlmax
  ))

  when(csr_cmd === CSR_V){
    csr_regfile(VL_ADDR) := vl,
    csr_regfile(VTYPE_ADDR) := vtype
  }

  //****************************
  // Write Back Stage
  //val wb_data = io.dmem.rdata
  val wb_data = MuxCase(alu_out, Seq(
    (wb_sel === WB_MEM) -> io.dmem.rdata,
    (wb_sel === WB_PC) -> pc_plus4,
    (wb_sel === WB_CSR) -> csr_rdata,
    (wb_sel === WB_VL) -> vl
  ))
  when(rf_wen === REN_S) {
    regfile(wb_addr) := wb_data
  }


  // for debug
  io.regfile(3)
  io.pc := pc_reg
  io.exit := (inst === UNIMP)
  printf(p"io.pc      : 0x${Hexadecimal(pc_reg)}\n")
  printf(p"pc_next    : 0x${Hexadecimal(pc_next)}\n")
  printf(p"inst       : 0x${Hexadecimal(inst)}\n")
  printf(p"rs1_addr   : $rs1_addr\n")
  printf(p"rs2_addr   : $rs2_addr\n")
  printf(p"wb_addr    : $wb_addr\n")
  printf(p"rs1_data   : 0x${Hexadecimal(rs1_data)}\n")
  printf(p"rs2_data   : 0x${Hexadecimal(rs2_data)}\n")
  printf(p"wb_data    : 0x${Hexadecimal(wb_data)}\n")
  printf(p"dmem.addr  : ${io.dmem.addr}\n")
  printf(p"dmem.rdata : ${io.dmem.rdata}\n")
  printf("---------\n")
}