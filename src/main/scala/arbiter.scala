package rocket

import Chisel._
import Node._
import Constants._
import uncore._

class HellaCacheArbiter(n: Int)(implicit conf: RocketConfiguration) extends Component
{
  val io = new Bundle {
    val requestor = Vec(n) { new HellaCacheIO()(conf.dcache) }.flip
    val mem = new HellaCacheIO()(conf.dcache)
  }

  val r_valid = io.requestor.map(r => Reg(r.req.valid))

  io.mem.req.valid := io.requestor.map(_.req.valid).reduce(_||_)
  io.requestor(0).req.ready := io.mem.req.ready
  for (i <- 1 until n)
    io.requestor(i).req.ready := io.requestor(i-1).req.ready && !io.requestor(i-1).req.valid

  io.mem.req.bits := io.requestor(n-1).req.bits
  io.mem.req.bits.tag := Cat(io.requestor(n-1).req.bits.tag, UFix(n-1, log2Up(n)))
  for (i <- n-2 to 0 by -1) {
    val req = io.requestor(i).req
    when (req.valid) {
      io.mem.req.bits.cmd := req.bits.cmd
      io.mem.req.bits.typ := req.bits.typ
      io.mem.req.bits.addr := req.bits.addr
      io.mem.req.bits.phys := req.bits.phys
      io.mem.req.bits.tag := Cat(req.bits.tag, UFix(i, log2Up(n)))
    }
    when (r_valid(i)) {
      io.mem.req.bits.kill := req.bits.kill
      io.mem.req.bits.data := req.bits.data
    }
  }

  for (i <- 0 until n) {
    val resp = io.requestor(i).resp
    val tag_hit = io.mem.resp.bits.tag(log2Up(n)-1,0) === UFix(i)
    resp.valid := io.mem.resp.valid && tag_hit
    io.requestor(i).xcpt := io.mem.xcpt
    resp.bits := io.mem.resp.bits
    resp.bits.tag := io.mem.resp.bits.tag >> UFix(log2Up(n))
    resp.bits.nack := io.mem.resp.bits.nack && tag_hit
    resp.bits.replay := io.mem.resp.bits.replay && tag_hit
  }
}
