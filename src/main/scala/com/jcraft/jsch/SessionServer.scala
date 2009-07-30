/* -*-mode:java; c-basic-offset:2; indent-tabs-mode:nil -*- */
/*
Copyright (c) 2009 ymnk, JCraft,Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

  1. Redistributions of source code must retain the above copyright notice,
     this list of conditions and the following disclaimer.

  2. Redistributions in binary form must reproduce the above copyright 
     notice, this list of conditions and the following disclaimer in 
     the documentation and/or other materials provided with the distribution.

  3. The names of the authors may not be used to endorse or promote products
     derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL JCRAFT,
INC. OR ANY CONTRIBUTORS TO THIS SOFTWARE BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.jcraft.jsch
import java.io._
import java.net._

class SessionServer(jsch:JSch, socket:Socket) extends Session(jsch)
                                              with SessionTrait{
  running_as_client = false

  def this(jsch:JSch) = this(jsch, null) 

  def setServerVersion(V_S:Array[Byte]){ setVS(V_S) }

  def writeVersionString(){
    val V_S = getVS
    val foo = new Array[Byte](V_S.length+2)
    System.arraycopy(V_S, 0, foo, 0, V_S.length);
    foo(foo.length-2) = 0x0d.asInstanceOf[Byte]
    foo(foo.length-1) = 0x0a.asInstanceOf[Byte]
    io.put(foo, 0, foo.length);
  }
 
  override def readVersionString():Array[Byte] = {
    val tmp = super.readVersionString
    setVC(tmp)
    tmp
  }

  def openConnection(connectTimeout:Int){
    import Session.random
    if(random == null){
      try{
	val c = Class.forName(getConfig("random"))
        random = c.newInstance.asInstanceOf[Random]
      }
      catch{case e => throw new JSchException(e.toString, e)}
    }
    Packet.setRandom(random)

    socket.setTcpNoDelay(true);
    io=new IO
    io.setInputStream(socket.getInputStream)
    io.setOutputStream(socket.getOutputStream)

    if(connectTimeout>0 && socket!=null){
      socket.setSoTimeout(connectTimeout)
    }
    isConnected=true
  }

  override def keyExchange(){super.keyExchange}
}
