package lw

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

class Memory extends Module {
  val io = IO(new Bundle {
    val imem = new ImemPortIo()
  })

  // generate 8bit width x 16384 (16KB) register
  val mem = Mem(16384, UInt(8.W))

  // load memory data from .hex
  loadMemoryFromFile(mem, "src/hex/fetch.hex")

  // "Cat" in p56
  //
  io.imem.inst := Cat(
    mem(io.imem.addr + 3.U(WORD_LEN.W)),
    mem(io.imem.addr + 2.U(WORD_LEN.W)),
    mem(io.imem.addr + 1.U(WORD_LEN.W)),
    mem(io.imem.addr)
  )

}