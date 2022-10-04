package vle

import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFile
import common.Consts._

/*  Bundle likes "Struct"
    addr : input_port for memory address (32bit width)
    inst : output_port for instruction data (32bit width) */
class ImemPortIo extends Bundle {
  val addr = Input(UInt(WORD_LEN.W))
  val inst = Output(UInt(WORD_LEN.W))
}

/*  addr : 
    rdata :
    wen : whether "write" is enabled. Limit "write" in some case
    wdata : write data*/
class DmemPortIo extends Bundle {
  val addr = Input(UInt(WORD_LEN.W))
  val rdata = Output(UInt(WORD_LEN.W))
  val wen = Input(Bool())
  val wdata = Input(UInt(WORD_LEN.W))
  val vrdata = Output(UInt((VLEN*8).W))
}

class Memory extends Module {
  val io = IO(new Bundle {
    val imem = new ImemPortIo()
    val dmem = new DmemPortIo()
  })

  // generate 8bit width x 16384 (16KB) register
  val mem = Mem(16384, UInt(8.W))

  // load memory data from .hex
  loadMemoryFromFile(mem, "src/hex/vle32.hex")

  // write data in Memory
  // "Cat" in p56
  io.imem.inst := Cat(
    mem(io.imem.addr + 3.U(WORD_LEN.W)),
    mem(io.imem.addr + 2.U(WORD_LEN.W)),
    mem(io.imem.addr + 1.U(WORD_LEN.W)),
    mem(io.imem.addr)
  )

  /*
  io.dmem.rdata := Cat(
    mem(io.dmem.addr + 3.U(WORD_LEN.W)),
    mem(io.dmem.addr + 2.U(WORD_LEN.W)),
    mem(io.dmem.addr + 1.U(WORD_LEN.W)),
    mem(io.dmem.addr)
  )
  */
  def readData(len: Int) = Cat(Seq.tabulate(len / 8)(n => mem(io.dmem.addr + n.U(WORD_LEN))).reverse)
  io.dmem.rdata := readData(WORD_LEN)
  io.dmem.vrdata := readData(VLEN * 8)

  when(io.dmem.wen) {
    mem(io.dmem.addr) := io.dmem.wdata(7, 0)
    mem(io.dmem.addr + 1.U) := io.dmem.wdata(15, 8)
    mem(io.dmem.addr + 2.U) := io.dmem.wdata(23, 16)
    mem(io.dmem.addr + 3.U) := io.dmem.wdata(31, 24)
  }
}