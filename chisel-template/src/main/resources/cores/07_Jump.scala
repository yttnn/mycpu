package jump

import chisel3._
import chisel3.util._
import common.Consts._
import common.Instructions._ 

class Core extends Module {
  val io = IO(new Bundle {
    val imem = Flipped(new ImemPortIo())
    val dmem = Flipped(new DmemPortIo())
    val exit = Output(Bool())
  })

  // generate 32bit x 32 register
  // WORD_LEN = 32 defined in Consts.scala
  val regfile = Mem(32, UInt(WORD_LEN.W))

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
    jmp_flg -> alu_out
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
  // Decode stage upgrade
  //p54-55
  val csignals = ListLookup(inst,
  List(ALU_X, OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X),
    Array(
      LW   -> List(ALU_ADD , OP1_RS1, OP2_IMI, MEN_X , REN_S, WB_MEM),
      SW   -> List(ALU_ADD , OP1_RS1, OP2_IMS, MEN_S , REN_X, WB_X  ),
      ADD  -> List(ALU_ADD , OP1_RS1, OP2_RS2, MEN_X , REN_S, WB_ALU),
      ADDI -> List(ALU_ADD , OP1_RS1, OP2_IMI, MEN_X , REN_S, WB_ALU),
      SUB  -> List(ALU_SUB , OP1_RS1, OP2_RS2, MEN_X , REN_S, WB_ALU),
      AND  -> List(ALU_AND , OP1_RS1, OP2_RS2, MEN_X , REN_S, WB_ALU),
      OR   -> List(ALU_OR  , OP1_RS1, OP2_RS2, MEN_X , REN_S, WB_ALU),
      XOR  -> List(ALU_XOR , OP1_RS1, OP2_RS2, MEN_X , REN_S, WB_ALU),
      ANDI -> List(ALU_AND , OP1_RS1, OP2_IMI, MEN_X , REN_S, WB_ALU),
      ORI  -> List(ALU_OR  , OP1_RS1, OP2_IMI, MEN_X , REN_S, WB_ALU),
      XORI -> List(ALU_XOR , OP1_RS1, OP2_IMI, MEN_X , REN_S, WB_ALU),
      SLL  -> List(ALU_SLL , OP1_RS1, OP2_RS2, MEN_X , REN_S, WB_ALU),
      SRL  -> List(ALU_SRL , OP1_RS1, OP2_RS2, MEN_X , REN_S, WB_ALU),
      SRA  -> List(ALU_SRA , OP1_RS1, OP2_RS2, MEN_X , REN_S, WB_ALU),
      SLLI -> List(ALU_SLL , OP1_RS1, OP2_IMI, MEN_X , REN_S, WB_ALU),
      SRLI -> List(ALU_SRL , OP1_RS1, OP2_IMI, MEN_X , REN_S, WB_ALU),
      SRAI -> List(ALU_SRA , OP1_RS1, OP2_IMI, MEN_X , REN_S, WB_ALU),
      SLT  -> List(ALU_SLT , OP1_RS1, OP2_RS2, MEN_X , REN_S, WB_ALU),
      SLTU -> List(ALU_SLTU, OP1_RS1, OP2_RS2, MEN_X , REN_S, WB_ALU),
      SLTI -> List(ALU_SLT , OP1_RS1, OP2_IMI, MEN_X , REN_S, WB_ALU),
      SLTIU-> List(ALU_SLTU, OP1_RS1, OP2_IMI, MEN_X , REN_S, WB_ALU),
      BEQ  -> List(BR_BEQ  , OP1_RS1, OP2_RS2, MEN_X , REN_X, WB_X  ),
      BNE  -> List(BR_BNE  , OP1_RS1, OP2_RS2, MEN_X , REN_X, WB_X  ),
      BGE  -> List(BR_BGE  , OP1_RS1, OP2_RS2, MEN_X , REN_X, WB_X  ),
      BGEU -> List(BR_BGEU , OP1_RS1, OP2_RS2, MEN_X , REN_X, WB_X  ),
      BLT  -> List(BR_BLT  , OP1_RS1, OP2_RS2, MEN_X , REN_X, WB_X  ),
      BLTU -> List(BR_BLTU , OP1_RS1, OP2_RS2, MEN_X , REN_X, WB_X  ),
      JAL  -> List(ALU_ADD , OP1_PC , OP2_IMJ, MEN_X , REN_S, WB_PC ),
      JALR -> List(ALU_JALR, OP1_RS1, OP2_IMI, MEN_X , REN_S, WB_PC )
    ))
  val exe_fun :: op1_sel :: op2_sel :: mem_wen :: rf_wen :: wb_sel :: Nil = csignals
  // identify instruction-type(R,S,I....) for Execute Stage
  val op1_data = MuxCase(0.U(WORD_LEN.W), Seq(
    (op1_sel === OP1_RS1) -> rs1_data,
    (op1_sel === OP1_PC ) -> pc_reg
  ))
  val op2_data = MuxCase(0.U(WORD_LEN.W), Seq(
    (op2_sel === OP2_RS2) -> rs2_data,
    (op2_sel === OP2_IMI) -> imm_i_sext,
    (op2_sel === OP2_IMS) -> imm_s_sext,
    (op2_sel === OP2_IMJ) -> imm_j_sext
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
    (exe_fun === ALU_JALR) -> ((op1_data + op2_data) & ~1.U(WORD_LEN.W))
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
  io.dmem.wen := mem_wen
  io.dmem.wdata := rs2_data

  //****************************
  // Write Back Stage
  //val wb_data = io.dmem.rdata
  val wb_data = MuxCase(alu_out, Seq(
    (wb_sel === WB_MEM) -> io.dmem.rdata,
    (wb_sel === WB_PC) -> pc_plus4
  ))
  when(rf_wen === REN_S) {
    regfile(wb_addr) := wb_data
  }


  // for debug
  //io.exit := (inst === 0x34333231.U(WORD_LEN.W))
  io.exit := (inst === 0x00602823.U(WORD_LEN.W))

  printf(p"pc_reg   : 0x${Hexadecimal(pc_reg)}\n")
  printf(p"inst     : 0x${Hexadecimal(inst)}\n")
  printf(p"rs1_addr : $rs1_addr\n")
  printf(p"rs2_addr : $rs2_addr\n")
  printf(p"wb_addr  : $wb_addr\n")
  printf(p"rs1_data : 0x${Hexadecimal(rs1_data)}\n")
  printf(p"rs2_data : 0x${Hexadecimal(rs2_data)}\n")
  printf(p"wb_data  : 0x${Hexadecimal(wb_data)}\n")
  printf(p"dmem.addr: ${io.dmem.addr}\n")
  printf(p"dmem.wen : ${io.dmem.wen}\n")
  printf(p"dmem.wdata: 0x${Hexadecimal(io.dmem.wdata)}\n")
  printf("---------\n")
}