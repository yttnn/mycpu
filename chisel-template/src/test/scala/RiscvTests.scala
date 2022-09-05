package riscvtests

import chisel3._
import org.scalatest._
import chiseltest._

class RiscvTest extends FlatSpec with ChiselScalalatestTester {
  behavior of "mycpu"
  it should "work through hex" in {
    test(new Top){
      c => while(!c.io.exit.peak().litToBoolean){
        c.clock.step(1)
      }
      c.io.gp.expect(1.U) // p154
    }
  }
}