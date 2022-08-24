package lw

import  chisel3._

class Top extends Module {
  // p47~48
  val io = IO(new Bundle {
    val exit = Output(Bool())
  })

  val core = Module(new Core())
  val memory = Module(new Memory())

  core.io.imem <> memory.io.imem
  core.io.dmem <> memory.io.dmem // add

  io.exit := core.io.exit
}