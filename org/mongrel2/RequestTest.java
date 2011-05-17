/* Copyright 2011 Armando Singer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mongrel2;

import static org.junit.Assert.assertEquals;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.regex.Pattern;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mongrel2.Handler.Request;

public class RequestTest {

  Charset ASCII = Charset.forName("US-ASCII");
  
  @BeforeClass public static void setUpBeforeClass() throws Exception {}

  @Before public void setUp() throws Exception {}
  
  @Test public final void testSimpleParse() {
    
    final String stringMsg = ("54c6755b-9628-40a4-9a2d-cc82a816345e 27 /handlertest " +
      "496:{\"PATH\":\"/handlertest\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\"," +
      "\"URI\":\"/handlertest\",\"Accept\":\"text/html,application/xhtml+xml,application/xml;q=0.9,/;q=0.8\"," +
      "\"Accept-Charset\":\"ISO-8859-1,utf-8;q=0.7,*;q=0.7\",\"Accept-Encoding\":\"gzip,deflate\", " +
      "\"Accept-Language\":\"en-us,en;q=0.5\",\"Connection\":\"keep-alive\",\"Host\":\"mongrel2.org:6767\", " +
      "\"Keep-Alive\":\"115\",\"Referer\":\"http://twitter.com/\"," +
      "\"User-Agent\":\"Mozilla/5.0 (X11; U; Linux x86_64; en-US; rv:1.9.2.6) " +
      "Gecko/20100628 Ubuntu/10.04 (lucid) Firefox/3.6.6\"},0:,");
    final byte[] bytes = stringMsg.getBytes(ASCII);
    
    final Request r = Request.parse(bytes, false);
    System.out.println(stringMsg.substring(57, stringMsg.length()));
    System.out.println("554: " + stringMsg.substring(554, stringMsg.length()));
    System.out.println("553: " + stringMsg.substring(553, stringMsg.length()));
    System.out.println(r);
    assertEquals("GET", r.getHeader("method"));
    assertEquals("/handlertest", r.getHeader("uri"));
  }

  @Test public final void testParse() {
    
    // from http://zedshaw.com/request_payloads.txt 
    final String manyStrings = "GET 0 / 76:{\"PATH\":\"/\",\"host\":\"default\",\"METHOD\":\"HEAD\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 75:6:METHOD,4:HEAD,3:URI,1:/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,4:host,7:default,}0:,\n" + 
    "GET 0 / 99:{\"PATH\":\"/\",\"accept-encoding\":\"foo\",\"host\":\"default\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 99:6:METHOD,3:GET,3:URI,1:/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,4:host,7:default,15:accept-encoding,3:foo,}0:,\n" + 
    "GET 0 /virt/tst_11/r503 125:{\"PATH\":\"/virt/tst_11/r503\",\"accept-encoding\":\"foo\",\"host\":\"\",\"METHOD\":\"HEAD\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/virt/tst_11/r503\"},0:,\n" + 
    "GET 0 /virt/tst_11/r503 127:6:METHOD,4:HEAD,3:URI,17:/virt/tst_11/r503,7:VERSION,8:HTTP/1.1,4:PATH,17:/virt/tst_11/r503,4:host,0:,15:accept-encoding,3:foo,}0:,\n" + 
    "GET 0 /virt/tst_11/lowercase-path/fOO.html.PL 168:{\"PATH\":\"/virt/tst_11/lowercase-path/fOO.html.PL\",\"accept-encoding\":\"foo\",\"host\":\"\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/virt/tst_11/lowercase-path/fOO.html.PL\"},0:,\n" + 
    "GET 0 /virt/tst_11/lowercase-path/fOO.html.PL 170:6:METHOD,3:GET,3:URI,39:/virt/tst_11/lowercase-path/fOO.html.PL,7:VERSION,8:HTTP/1.1,4:PATH,39:/virt/tst_11/lowercase-path/fOO.html.PL,4:host,0:,15:accept-encoding,3:foo,}0:,\n" + 
    "GET 0 /01_. 76:{\"PATH\":\"/01_.\",\"host\":\"\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/01_.\"},0:,\n" + 
    "GET 0 /01_. 75:6:METHOD,3:GET,3:URI,5:/01_.,7:VERSION,8:HTTP/1.1,4:PATH,5:/01_.,4:host,0:,}0:,\n" + 
    "GET 0 /403/1 103:{\"PATH\":\"/403/1\",\"accept-encoding\":\"foo\",\"host\":\"\",\"METHOD\":\"HEAD\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/403/1\"},0:,\n" + 
    "GET 0 /403/1 103:6:METHOD,4:HEAD,3:URI,6:/403/1,7:VERSION,8:HTTP/1.1,4:PATH,6:/403/1,4:host,0:,15:accept-encoding,3:foo,}0:,\n" + 
    "GET 0 /bp.x.y 96:{\"PATH\":\"/bp.x.y\",\"host\":\"foo.example.com\",\"METHOD\":\"HEAD\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/bp.x.y\"},0:,\n" + 
    "GET 0 /bp.x.y 96:6:METHOD,4:HEAD,3:URI,7:/bp.x.y,7:VERSION,8:HTTP/1.1,4:PATH,7:/bp.x.y,4:host,15:foo.example.com,}0:,\n" + 
    "GET 0 /conf-tst13/cond 98:{\"PATH\":\"/conf-tst13/cond\",\"host\":\"\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/conf-tst13/cond\"},0:,\n" + 
    "GET 0 /conf-tst13/cond 99:6:METHOD,3:GET,3:URI,16:/conf-tst13/cond,7:VERSION,8:HTTP/1.1,4:PATH,16:/conf-tst13/cond,4:host,0:,}0:,\n" + 
    "GET 0 /neg 74:{\"PATH\":\"/neg\",\"host\":\"\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/neg\"},0:,\n" + 
    "GET 0 /neg 73:6:METHOD,3:GET,3:URI,4:/neg,7:VERSION,8:HTTP/1.1,4:PATH,4:/neg,4:host,0:,}0:,\n" + 
    "GET 0 /conf-tst13/hex 96:{\"PATH\":\"/conf-tst13/hex\",\"host\":\"\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/conf-tst13/hex\"},0:,\n" + 
    "GET 0 /conf-tst13/hex 97:6:METHOD,3:GET,3:URI,15:/conf-tst13/hex,7:VERSION,8:HTTP/1.1,4:PATH,15:/conf-tst13/hex,4:host,0:,}0:,\n" + 
    "GET 0 /A2/neg 113:{\"PATH\":\"/A2/neg\",\"host\":\"\",\"x-comment\":\"Doing with no auth\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/A2/neg\"},0:,\n" + 
    "GET 0 /A2/neg 113:6:METHOD,3:GET,3:URI,7:/A2/neg,7:VERSION,8:HTTP/1.1,4:PATH,7:/A2/neg,9:x-comment,18:Doing with no auth,4:host,0:,}0:,\n" + 
    "GET 0 /A2/conf-tst13/hex 139:{\"PATH\":\"/A2/conf-tst13/hex\",\"host\":\"\",\"authorization\":\"Basic Zm9vOmJhcg==\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/A2/conf-tst13/hex\"},0:,\n" + 
    "GET 0 /A2/conf-tst13/hex 142:6:METHOD,3:GET,3:URI,18:/A2/conf-tst13/hex,7:VERSION,8:HTTP/1.1,4:PATH,18:/A2/conf-tst13/hex,13:authorization,18:Basic Zm9vOmJhcg==,4:host,0:,}0:,\n" + 
    "GET 0 /conf-tst13/cond 98:{\"PATH\":\"/conf-tst13/cond\",\"host\":\"\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/conf-tst13/cond\"},0:,\n" + 
    "GET 0 /conf-tst13/cond 99:6:METHOD,3:GET,3:URI,16:/conf-tst13/cond,7:VERSION,8:HTTP/1.1,4:PATH,16:/conf-tst13/cond,4:host,0:,}0:,\n" + 
    "GET 0 /index.html 104:{\"PATH\":\"/index.html\",\"connection\":\"keep-alive\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.0\",\"URI\":\"/index.html\"},0:,\n" + 
    "GET 0 /index.html 107:6:METHOD,3:GET,3:URI,11:/index.html,7:VERSION,8:HTTP/1.0,4:PATH,11:/index.html,10:connection,10:keep-alive,}0:,\n" + 
    "GET 0 /nothere/5.1-switch-5.2 129:{\"PATH\":\"/nothere/5.1-switch-5.2\",\"connection\":\"keep-alive\",\"METHOD\":\"HEAD\",\"VERSION\":\"HTTP/1.0\",\"URI\":\"/nothere/5.1-switch-5.2\"},0:,\n" + 
    "GET 0 /nothere/5.1-switch-5.2 132:6:METHOD,4:HEAD,3:URI,23:/nothere/5.1-switch-5.2,7:VERSION,8:HTTP/1.0,4:PATH,23:/nothere/5.1-switch-5.2,10:connection,10:keep-alive,}0:,\n" + 
    "GET 0 /nothere/5.1-switch-5.2 129:{\"PATH\":\"/nothere/5.1-switch-5.2\",\"connection\":\"keep-alive\",\"METHOD\":\"HEAD\",\"VERSION\":\"HTTP/1.0\",\"URI\":\"/nothere/5.1-switch-5.2\"},0:,\n" + 
    "GET 0 /nothere/5.1-switch-5.2 132:6:METHOD,4:HEAD,3:URI,23:/nothere/5.1-switch-5.2,7:VERSION,8:HTTP/1.0,4:PATH,23:/nothere/5.1-switch-5.2,10:connection,10:keep-alive,}0:,\n" + 
    "GET 0 /nothere/5.1-switch-5.2 129:{\"PATH\":\"/nothere/5.1-switch-5.2\",\"connection\":\"keep-alive\",\"METHOD\":\"HEAD\",\"VERSION\":\"HTTP/1.0\",\"URI\":\"/nothere/5.1-switch-5.2\"},0:,\n" + 
    "GET 0 /nothere/5.1-switch-5.2 132:6:METHOD,4:HEAD,3:URI,23:/nothere/5.1-switch-5.2,7:VERSION,8:HTTP/1.0,4:PATH,23:/nothere/5.1-switch-5.2,10:connection,10:keep-alive,}0:,\n" + 
    "GET 0 /index.html/foo 113:{\"PATH\":\"/index.html/foo\",\"connection\":\"keep-alive\",\"METHOD\":\"HEAD\",\"VERSION\":\"HTTP/1.0\",\"URI\":\"/index.html/foo\"},0:,\n" + 
    "GET 0 /index.html/foo 116:6:METHOD,4:HEAD,3:URI,15:/index.html/foo,7:VERSION,8:HTTP/1.0,4:PATH,15:/index.html/foo,10:connection,10:keep-alive,}0:,\n" + 
    "GET 0 / 83:{\"PATH\":\"/\",\"host\":\"foo.EXAMPLE.Com\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 83:6:METHOD,3:GET,3:URI,1:/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,4:host,15:foo.EXAMPLE.Com,}0:,\n" + 
    "GET 0 / 116:{\"PATH\":\"/\",\"accept\":\"text/*, text/html;q=0\",\"host\":\"foo.EXAMPLE.Com\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 117:6:METHOD,3:GET,3:URI,1:/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,4:host,15:foo.EXAMPLE.Com,6:accept,21:text/*, text/html;q=0,}0:,\n" + 
    "GET 0 / 85:{\"PATH\":\"/\",\"connection\":\"keep-alive\",\"METHOD\":\"HEAD\",\"VERSION\":\"HTTP/1.0\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 86:6:METHOD,4:HEAD,3:URI,1:/,7:VERSION,8:HTTP/1.0,4:PATH,1:/,10:connection,10:keep-alive,}0:,\n" + 
    "GET 0 / 158:{\"PATH\":\"/\",\"host\":\"foo.exAMPLE.COM\",\"if-none-match\":[\"\\\"f.e.c/1\\\"\",\"\\\"f.e.c/2\\\"\",\"\\\"f.e.c/3\\\"\",\"\\\"f.e.c/4\\\"\"],\"METHOD\":\"HEAD\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 153:6:METHOD,4:HEAD,3:URI,1:/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,13:if-none-match,48:9:\"f.e.c/1\",9:\"f.e.c/2\",9:\"f.e.c/3\",9:\"f.e.c/4\",]4:host,15:foo.exAMPLE.COM,}0:,\n" + 
    "GET 0 / 85:{\"PATH\":\"/\",\"connection\":\"keep-alive\",\"METHOD\":\"HEAD\",\"VERSION\":\"HTTP/1.0\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 86:6:METHOD,4:HEAD,3:URI,1:/,7:VERSION,8:HTTP/1.0,4:PATH,1:/,10:connection,10:keep-alive,}0:,\n" + 
    "GET 0 / 158:{\"PATH\":\"/\",\"host\":\"foo.exAMPLE.COM\",\"if-none-match\":[\"\\\"f.e.c/1\\\"\",\"\\\"f.e.c/2\\\"\",\"\\\"f.e.c/3\\\"\",\"\\\"f.e.c/4\\\"\"],\"METHOD\":\"HEAD\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 153:6:METHOD,4:HEAD,3:URI,1:/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,13:if-none-match,48:9:\"f.e.c/1\",9:\"f.e.c/2\",9:\"f.e.c/3\",9:\"f.e.c/4\",]4:host,15:foo.exAMPLE.COM,}0:,\n" + 
    "GET 0 / 85:{\"PATH\":\"/\",\"connection\":\"keep-alive\",\"METHOD\":\"HEAD\",\"VERSION\":\"HTTP/1.0\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 86:6:METHOD,4:HEAD,3:URI,1:/,7:VERSION,8:HTTP/1.0,4:PATH,1:/,10:connection,10:keep-alive,}0:,\n" + 
    "GET 0 / 158:{\"PATH\":\"/\",\"host\":\"foo.exAMPLE.COM\",\"if-none-match\":[\"\\\"f.e.c/1\\\"\",\"\\\"f.e.c/2\\\"\",\"\\\"f.e.c/3\\\"\",\"\\\"f.e.c/4\\\"\"],\"METHOD\":\"HEAD\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 153:6:METHOD,4:HEAD,3:URI,1:/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,13:if-none-match,48:9:\"f.e.c/1\",9:\"f.e.c/2\",9:\"f.e.c/3\",9:\"f.e.c/4\",]4:host,15:foo.exAMPLE.COM,}0:,\n" + 
    "GET 0 / 85:{\"PATH\":\"/\",\"connection\":\"keep-alive\",\"METHOD\":\"HEAD\",\"VERSION\":\"HTTP/1.0\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 86:6:METHOD,4:HEAD,3:URI,1:/,7:VERSION,8:HTTP/1.0,4:PATH,1:/,10:connection,10:keep-alive,}0:,\n" + 
    "GET 0 / 158:{\"PATH\":\"/\",\"host\":\"foo.exAMPLE.COM\",\"if-none-match\":[\"\\\"f.e.c/1\\\"\",\"\\\"f.e.c/2\\\"\",\"\\\"f.e.c/3\\\"\",\"\\\"f.e.c/4\\\"\"],\"METHOD\":\"HEAD\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 153:6:METHOD,4:HEAD,3:URI,1:/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,13:if-none-match,48:9:\"f.e.c/1\",9:\"f.e.c/2\",9:\"f.e.c/3\",9:\"f.e.c/4\",]4:host,15:foo.exAMPLE.COM,}0:,\n" + 
    "GET 0 / 70:{\"PATH\":\"/\",\"host\":\".\",\"METHOD\":\"HEAD\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 69:6:METHOD,4:HEAD,3:URI,1:/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,4:host,1:.,}0:,\n" + 
    "GET 0 / 70:{\"PATH\":\"/\",\"host\":\".\",\"METHOD\":\"HEAD\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 69:6:METHOD,4:HEAD,3:URI,1:/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,4:host,1:.,}0:,\n" + 
    "GET 0 / 73:{\"PATH\":\"/\",\"host\":\"abcd\",\"METHOD\":\"BLAH\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 72:6:METHOD,4:BLAH,3:URI,1:/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,4:host,4:abcd,}0:,\n" + 
    "GET 0 / 61:{\"PATH\":\"/\",\"METHOD\":\"DELETE\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 60:6:METHOD,6:DELETE,3:URI,1:/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,}0:,\n" + 
    "GET 0 / 107:{\"PATH\":\"/\",\"connection\":\"close\",\"host\":\"foo.example.com\",\"METHOD\":\"DELETE\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 108:6:METHOD,6:DELETE,3:URI,1:/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,4:host,15:foo.example.com,10:connection,5:close,}0:,\n" + 
    "GET 0 / 120:{\"PATH\":\"/\",\"expect\":\"100-continue\",\"connection\":\"close\",\"host\":\"default\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 121:6:METHOD,3:GET,3:URI,1:/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,4:host,7:default,10:connection,5:close,6:expect,12:100-continue,}0:,\n" + 
    "GET 0 / 89:{\"PATH\":\"/\",\"range\":\"1-2\",\"host\":\"default\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 88:6:METHOD,3:GET,3:URI,1:/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,4:host,7:default,5:range,3:1-2,}0:,\n" + 
    "GET 0 / 129:{\"PATH\":\"/\",\"if-unmodified-since\":\"Tue, 12 Oct 2004 07:02:24 GMT\",\"host\":\"default\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 130:6:METHOD,3:GET,3:URI,1:/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,4:host,7:default,19:if-unmodified-since,29:Tue, 12 Oct 2004 07:02:24 GMT,}0:,\n" + 
    "GET 0 / 59:{\"PATH\":\"/\",\"METHOD\":\"BLAH\",\"VERSION\":\"HTTP/1.0\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 58:6:METHOD,4:BLAH,3:URI,1:/,7:VERSION,8:HTTP/1.0,4:PATH,1:/,}0:,\n" + 
    "GET 0 / 74:{\"PATH\":\"/\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"http://default:x/\"},0:,\n" + 
    "GET 0 / 74:6:METHOD,3:GET,3:URI,17:http://default:x/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,}0:,\n" + 
    "GET 0 /fifo 104:{\"PATH\":\"/fifo\",\"connection\":\"close\",\"host\":\"default\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/fifo\"},0:,\n" + 
    "GET 0 /fifo 104:6:METHOD,3:GET,3:URI,5:/fifo,7:VERSION,8:HTTP/1.1,4:PATH,5:/fifo,4:host,7:default,10:connection,5:close,}0:,\n" + 
    "GET 0 //fifo 123:{\"PATH\":\"//fifo\",\"connection\":\"close\",\"host\":\"default\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"//fifo\",\"FRAGMENT\":\"seg\"},0:,\n" + 
    "GET 0 //fifo 123:6:METHOD,3:GET,8:FRAGMENT,3:seg,3:URI,6://fifo,7:VERSION,8:HTTP/1.1,4:PATH,6://fifo,4:host,7:default,10:connection,5:close,}0:,\n" + 
    "GET 0 /%2f 77:{\"PATH\":\"/%2f\",\"host\":\"def\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/%2f\"},0:,\n" + 
    "GET 0 /%2f 76:6:METHOD,3:GET,3:URI,4:/%2f,7:VERSION,8:HTTP/1.1,4:PATH,4:/%2f,4:host,3:def,}0:,\n" + 
    "GET 0 /passwd 105:{\"PATH\":\"/passwd\",\"connection\":\"close\",\"host\":\"/etc\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/passwd\"},0:,\n" + 
    "GET 0 /passwd 105:6:METHOD,3:GET,3:URI,7:/passwd,7:VERSION,8:HTTP/1.1,4:PATH,7:/passwd,4:host,4:/etc,10:connection,5:close,}0:,\n" + 
    "GET 0 / 108:{\"PATH\":\"/\",\"accept-encoding\":\"blah\",\"host\":\"\",\"METHOD\":\"HEAD\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"http://default/\"},0:,\n" + 
    "GET 0 / 109:6:METHOD,4:HEAD,3:URI,15:http://default/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,4:host,0:,15:accept-encoding,4:blah,}0:,\n" + 
    "GET 0 / 89:{\"PATH\":\"/\",\"content-length\":\"999\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"http://x/\"},0:,\n" + 
    "GET 0 / 89:6:METHOD,3:GET,3:URI,9:http://x/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,14:content-length,3:999,}0:,\n" + 
    "GET 0 / 96:{\"PATH\":\"/\",\"transfer-encoding\":\"chunked\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"http://x/\"},0:,\n" + 
    "GET 0 / 96:6:METHOD,3:GET,3:URI,9:http://x/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,17:transfer-encoding,7:chunked,}0:,\n" + 
    "GET 0 / 93:{\"PATH\":\"/\",\"host\":\"\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"http://default/index.html~\"},0:,\n" + 
    "GET 0 / 93:6:METHOD,3:GET,3:URI,26:http://default/index.html~,7:VERSION,8:HTTP/1.1,4:PATH,1:/,4:host,0:,}0:,\n" + 
    "GET 0 / 103:{\"PATH\":\"/\",\"accept\":\"a/b ; blah=\\\"\\\\\",\"host\":\"default\",\"METHOD\":\"HEAD\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 101:6:METHOD,4:HEAD,3:URI,1:/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,4:host,7:default,6:accept,13:a/b ; blah=\"\\,}0:,\n" + 
    "GET 0 / 76:{\"PATH\":\"/\",\"host\":\"default\",\"METHOD\":\"HEAD\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 75:6:METHOD,4:HEAD,3:URI,1:/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,4:host,7:default,}0:,\n" + 
    "GET 0 / 134:{\"PATH\":\"/\",\"range\":\"bytes = 1-\",\"connection\":\"close\",\"host\":\"default\",\"if-range\":\"foo\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 135:6:METHOD,3:GET,3:URI,1:/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,8:if-range,3:foo,4:host,7:default,10:connection,5:close,5:range,10:bytes = 1-,}0:,\n" + 
    "GET 0 / 70:{\"PATH\":\"/\",\"host\":\".\",\"METHOD\":\"HEAD\",\"VERSION\":\"HTTP/1.0\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 69:6:METHOD,4:HEAD,3:URI,1:/,7:VERSION,8:HTTP/1.0,4:PATH,1:/,4:host,1:.,}0:,\n" + 
    "GET 0 / 71:{\"PATH\":\"/\",\"host\":\"..\",\"METHOD\":\"HEAD\",\"VERSION\":\"HTTP/1.0\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 70:6:METHOD,4:HEAD,3:URI,1:/,7:VERSION,8:HTTP/1.0,4:PATH,1:/,4:host,2:..,}0:,\n" + 
    "GET 0 / 104:{\"PATH\":\"/\",\"content-type\":\"foo\",\"host\":\"foo.example.com\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 105:6:METHOD,3:GET,3:URI,1:/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,4:host,15:foo.example.com,12:content-type,3:foo,}0:,\n" + 
    "GET 0 / 108:{\"PATH\":\"/\",\"content-language\":\"foo\",\"host\":\"foo.example.com\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 109:6:METHOD,3:GET,3:URI,1:/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,4:host,15:foo.example.com,16:content-language,3:foo,}0:,\n" + 
    "GET 0 / 103:{\"PATH\":\"/\",\"transfer-encoding\":\"identity\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"http://default/\"},0:,\n" + 
    "GET 0 / 104:6:METHOD,3:GET,3:URI,15:http://default/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,17:transfer-encoding,8:identity,}0:,\n" + 
    "GET 0 / 134:{\"PATH\":\"/\",\"transfer-encoding\":\"identity\",\"content-length\":\"0\",\"host\":\"\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"http://default/\"},0:,\n" + 
    "GET 0 / 136:6:METHOD,3:GET,3:URI,15:http://default/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,4:host,0:,14:content-length,1:0,17:transfer-encoding,8:identity,}0:,\n" + 
    "GET 0 / 69:{\"PATH\":\"/\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"http://x.y./\"},0:,\n" + 
    "GET 0 / 69:6:METHOD,3:GET,3:URI,12:http://x.y./,7:VERSION,8:HTTP/1.1,4:PATH,1:/,}0:,\n" + 
    "GET 0 / 66:{\"PATH\":\"/\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"http://./\"},0:,\n" + 
    "GET 0 / 65:6:METHOD,3:GET,3:URI,9:http://./,7:VERSION,8:HTTP/1.1,4:PATH,1:/,}0:,\n" + 
    "GET 0 / 69:{\"PATH\":\"/\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"http://x.y!/\"},0:,\n" + 
    "GET 0 / 69:6:METHOD,3:GET,3:URI,12:http://x.y!/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,}0:,\n" + 
    "GET 0 / 69:{\"PATH\":\"/\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"http://x..y/\"},0:,\n" + 
    "GET 0 / 69:6:METHOD,3:GET,3:URI,12:http://x..y/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,}0:,\n" + 
    "GET 0 / 69:{\"PATH\":\"/\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"http://.x.y/\"},0:,\n" + 
    "GET 0 / 69:6:METHOD,3:GET,3:URI,12:http://.x.y/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,}0:,\n" + 
    "GET 0 //// 78:{\"PATH\":\"////\",\"host\":\"none\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"////\"},0:,\n" + 
    "GET 0 //// 77:6:METHOD,3:GET,3:URI,4:////,7:VERSION,8:HTTP/1.1,4:PATH,4:////,4:host,4:none,}0:,\n" + 
    "GET 0 /foobar 88:{\"PATH\":\"/foobar\",\"host\":\"none\",\"METHOD\":\"OPTIONS\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/foobar\"},0:,\n" + 
    "GET 0 /foobar 87:6:METHOD,7:OPTIONS,3:URI,7:/foobar,7:VERSION,8:HTTP/1.1,4:PATH,7:/foobar,4:host,4:none,}0:,\n" + 
    "GET 0 / 95:{\"PATH\":\"/\",\"range\":\"bytes=1-2\",\"host\":\"default\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 94:6:METHOD,3:GET,3:URI,1:/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,4:host,7:default,5:range,9:bytes=1-2,}0:,\n" + 
    "GET 0 /README 89:{\"PATH\":\"/README\",\"accept\":\"foo/bar\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.0\",\"URI\":\"/README\"},0:,\n" + 
    "GET 0 /README 88:6:METHOD,3:GET,3:URI,7:/README,7:VERSION,8:HTTP/1.0,4:PATH,7:/README,6:accept,7:foo/bar,}0:,\n" + 
    "GET 0 / 112:{\"PATH\":\"/\",\"accept-encoding\":\"gzip;q=0, identity;q=0\",\"host\":\"x\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 113:6:METHOD,3:GET,3:URI,1:/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,4:host,1:x,15:accept-encoding,22:gzip;q=0, identity;q=0,}0:,\n" + 
    "GET 0 /index.html 109:{\"PATH\":\"/index.html\",\"host\":\"www.foo.example.com..\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/index.html\"},0:,\n" + 
    "GET 0 /index.html 111:6:METHOD,3:GET,3:URI,11:/index.html,7:VERSION,8:HTTP/1.1,4:PATH,11:/index.html,4:host,21:www.foo.example.com..,}0:,\n" + 
    "GET 0 /foobar 91:{\"PATH\":\"/foobar\",\"host\":\"default\",\"METHOD\":\"OPTIONS\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/foobar\"},0:,\n" + 
    "GET 0 /foobar 90:6:METHOD,7:OPTIONS,3:URI,7:/foobar,7:VERSION,8:HTTP/1.1,4:PATH,7:/foobar,4:host,7:default,}0:,\n" + 
    "GET 0 /index.html 112:{\"PATH\":\"/index.html\",\"host\":\"www.foo.example.com..:80\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/index.html\"},0:,\n" + 
    "GET 0 /index.html 114:6:METHOD,3:GET,3:URI,11:/index.html,7:VERSION,8:HTTP/1.1,4:PATH,11:/index.html,4:host,24:www.foo.example.com..:80,}0:,\n" + 
    "GET 0 /index.html 114:{\"PATH\":\"/index.html\",\"host\":\"www.foo.example.com..:1234\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/index.html\"},0:,\n" + 
    "GET 0 /index.html 116:6:METHOD,3:GET,3:URI,11:/index.html,7:VERSION,8:HTTP/1.1,4:PATH,11:/index.html,4:host,26:www.foo.example.com..:1234,}0:,\n" + 
    "GET 0 / 100:{\"PATH\":\"/\",\"accept-encoding\":\"gzip\",\"host\":\"default\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 100:6:METHOD,3:GET,3:URI,1:/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,4:host,7:default,15:accept-encoding,4:gzip,}0:,\n" + 
    "GET 0 /.* 62:{\"PATH\":\"/.*\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.0\",\"URI\":\"/.*\"},0:,\n" + 
    "GET 0 /.* 61:6:METHOD,3:GET,3:URI,3:/.*,7:VERSION,8:HTTP/1.0,4:PATH,3:/.*,}0:,\n" + 
    "GET 0 /.400 66:{\"PATH\":\"/.400\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.0\",\"URI\":\"/.400\"},0:,\n" + 
    "GET 0 /.400 65:6:METHOD,3:GET,3:URI,5:/.400,7:VERSION,8:HTTP/1.0,4:PATH,5:/.400,}0:,\n" + 
    "GET 0 /.404 66:{\"PATH\":\"/.404\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.0\",\"URI\":\"/.404\"},0:,\n" + 
    "GET 0 /.404 65:6:METHOD,3:GET,3:URI,5:/.404,7:VERSION,8:HTTP/1.0,4:PATH,5:/.404,}0:,\n" + 
    "GET 0 /.410 66:{\"PATH\":\"/.410\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.0\",\"URI\":\"/.410\"},0:,\n" + 
    "GET 0 /.410 65:6:METHOD,3:GET,3:URI,5:/.410,7:VERSION,8:HTTP/1.0,4:PATH,5:/.410,}0:,\n" + 
    "GET 0 /.500 66:{\"PATH\":\"/.500\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.0\",\"URI\":\"/.500\"},0:,\n" + 
    "GET 0 /.500 65:6:METHOD,3:GET,3:URI,5:/.500,7:VERSION,8:HTTP/1.0,4:PATH,5:/.500,}0:,\n" + 
    "GET 0 /.503 66:{\"PATH\":\"/.503\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.0\",\"URI\":\"/.503\"},0:,\n" + 
    "GET 0 /.503 65:6:METHOD,3:GET,3:URI,5:/.503,7:VERSION,8:HTTP/1.0,4:PATH,5:/.503,}0:,\n" + 
    "GET 0 /.999 66:{\"PATH\":\"/.999\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.0\",\"URI\":\"/.999\"},0:,\n" + 
    "GET 0 /.999 65:6:METHOD,3:GET,3:URI,5:/.999,7:VERSION,8:HTTP/1.0,4:PATH,5:/.999,}0:,\n" + 
    "GET 0 / 109:{\"PATH\":\"/\",\"connection\":\"close\",\"host\":\"\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"http://:/noprivs.html\"},0:,\n" + 
    "GET 0 / 110:6:METHOD,3:GET,3:URI,21:http://:/noprivs.html,7:VERSION,8:HTTP/1.1,4:PATH,1:/,4:host,0:,10:connection,5:close,}0:,\n" + 
    "GET 0 / 103:{\"PATH\":\"/\",\"host\":\"\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"http://defaulT/bin\",\"FRAGMENT\":\"blah\"},0:,\n" + 
    "GET 0 / 103:6:METHOD,3:GET,8:FRAGMENT,4:blah,3:URI,18:http://defaulT/bin,7:VERSION,8:HTTP/1.1,4:PATH,1:/,4:host,0:,}0:,\n" + 
    "GET 0 / 89:{\"PATH\":\"/\",\"host\":\"\",\"METHOD\":\"HEAD\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"http://default/README\"},0:,\n" + 
    "GET 0 / 89:6:METHOD,4:HEAD,3:URI,21:http://default/README,7:VERSION,8:HTTP/1.1,4:PATH,1:/,4:host,0:,}0:,\n" + 
    "GET 0 / 80:{\"PATH\":\"/\",\"accept\":\"\",\"host\":\"\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 79:6:METHOD,3:GET,3:URI,1:/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,4:host,0:,6:accept,0:,}0:,\n" + 
    "GET 0 / 79:{\"PATH\":\"/\",\"host\":\"notcorrect\",\"METHOD\":\"HEAD\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 79:6:METHOD,4:HEAD,3:URI,1:/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,4:host,10:notcorrect,}0:,\n" + 
    "GET 0 / 156:{\"PATH\":\"/\",\"range\":\"bytes = -2\",\"connection\":\"ranGe, AccEpt, keep-aLive, accept-language\",\"accept\":\"foo/bar\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.0\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 158:6:METHOD,3:GET,3:URI,1:/,7:VERSION,8:HTTP/1.0,4:PATH,1:/,6:accept,7:foo/bar,10:connection,42:ranGe, AccEpt, keep-aLive, accept-language,5:range,10:bytes = -2,}0:,\n" + 
    "GET 0 / 79:{\"PATH\":\"/\",\"host\":\"default\",\"METHOD\":\"OPTIONS\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"*\"},0:,\n" + 
    "GET 0 / 78:6:METHOD,7:OPTIONS,3:URI,1:*,7:VERSION,8:HTTP/1.1,4:PATH,1:/,4:host,7:default,}0:,\n" + 
    "GET 0 /test 370:{\"PATH\":\"/test\",\"if-unmodified-since\":\"Sunday, 10-Oct-04 07:02:24 GMT\",\"duplicate\":[\"VAL1\",\"VAL2\",\"VAL3\"],\"if-modified-since\":\"Tue, 17 Aug 10 03:21:45 +0000\",\"host\":\"foo.example.com:1234\",\"comment-date-rfc1123\":\"Sun, 10 Oct 2004 07:02:24 GMT\",\"comment-tst\":\"Test different date formats...\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/test?howdy=yes\",\"QUERY\":\"howdy=yes\"},0:,\n" + 
    "GET 0 /test 381:6:METHOD,3:GET,5:QUERY,9:howdy=yes,3:URI,15:/test?howdy=yes,7:VERSION,8:HTTP/1.1,4:PATH,5:/test,11:comment-tst,30:Test different date formats...,20:comment-date-rfc1123,29:Sun, 10 Oct 2004 07:02:24 GMT,4:host,20:foo.example.com:1234,17:if-modified-since,29:Tue, 17 Aug 10 03:21:45 +0000,9:duplicate,21:4:VAL1,4:VAL2,4:VAL3,]19:if-unmodified-since,30:Sunday, 10-Oct-04 07:02:24 GMT,}0:,\n" + 
    "GET 0 /empty 93:{\"PATH\":\"/empty\",\"host\":\"foo.example.com\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/empty\"},0:,\n" + 
    "GET 0 /empty 93:6:METHOD,3:GET,3:URI,6:/empty,7:VERSION,8:HTTP/1.1,4:PATH,6:/empty,4:host,15:foo.example.com,}0:,\n" + 
    "GET 0 / 82:{\"PATH\":\"/\",\"accept\":\"text/*;q=1.0\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.0\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 82:6:METHOD,3:GET,3:URI,1:/,7:VERSION,8:HTTP/1.0,4:PATH,1:/,6:accept,12:text/*;q=1.0,}0:,\n" + 
    "GET 0 / 97:{\"PATH\":\"/\",\"cookie\":[\"foo=bar\",\"test=yes; go=no\"],\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.0\",\"URI\":\"/\"},0:,\n" + 
    "GET 0 / 99:6:METHOD,3:GET,3:URI,1:/,7:VERSION,8:HTTP/1.0,4:PATH,1:/,6:cookie,29:7:foo=bar,15:test=yes; go=no,]}0:,\n" + 
    "GET 0 / 108:{\"PATH\":\"/\",\"accept-encoding\":\"gzip\",\"host\":\"\",\"METHOD\":\"HEAD\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"http://default/\"},0:,\n" + 
    "GET 0 / 109:6:METHOD,4:HEAD,3:URI,15:http://default/,7:VERSION,8:HTTP/1.1,4:PATH,1:/,4:host,0:,15:accept-encoding,4:gzip,}0:,\n" + 
    "GET 0 /corner/ 97:{\"PATH\":\"/corner/\",\"host\":\"foo.example.com\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"/corner/\"},0:,\n" + 
    "GET 0 /corner/ 97:6:METHOD,3:GET,3:URI,8:/corner/,7:VERSION,8:HTTP/1.1,4:PATH,8:/corner/,4:host,15:foo.example.com,}0:,\n" + 
    "GET 0 / 127:{\"PATH\":\"/\",\"connection\":\"close\",\"host\":\"\",\"METHOD\":\"HEAD\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"http://foo.example.com:1234/bt.torrent\"},0:,\n" + 
    "GET 0 / 128:6:METHOD,4:HEAD,3:URI,38:http://foo.example.com:1234/bt.torrent,7:VERSION,8:HTTP/1.1,4:PATH,1:/,4:host,0:,10:connection,5:close,}0:,\n" + 
    "GET 0 <policy-file-request 46:{\"PATH\":\"<policy-file-request\",\"METHOD\":\"XML\"},0:,\n" + 
    "GET 0 <policy-file-request 46:6:METHOD,3:XML,4:PATH,20:<policy-file-request,}0:,\n" + 
    "GET 0 / 105:{\"PATH\":\"/\",\"host\":\"localhost\",\"METHOD\":\"GET\",\"VERSION\":\"HTTP/1.1\",\"URI\":\"http://localhost:8080/iesucks\"},0:,\n" + 
    "GET 0 / 105:6:METHOD,3:GET,3:URI,29:http://localhost:8080/iesucks,7:VERSION,8:HTTP/1.1,4:PATH,1:/,4:host,9:localhost,}0:,";
    
    final Scanner scanner = new Scanner(manyStrings).useDelimiter(Pattern.compile("\n"));
    Request first = null;
    int i = 1;
    while (scanner.hasNext()) {

      if (i % 2 == 0) {
        final Request second = Request.parse(scanner.next().getBytes(ASCII), false);
        final Map<String, List<String>> firstHeaders = first.getHeaders();
        final Map<String, List<String>> secondHeaders = second.getHeaders();
        
        for (final Entry<String, List<String>> entrySecond : secondHeaders.entrySet()) {
          assertEquals(entrySecond.getValue(), firstHeaders.get(entrySecond.getKey()));
        }
                
        for (final Entry<String, List<String>> entryFirst : firstHeaders.entrySet()) {
          assertEquals(entryFirst.getValue(), secondHeaders.get(entryFirst.getKey()));
        }

        assertEquals(firstHeaders.size(), secondHeaders.size());
      } else {
        first = Request.parse(scanner.next().getBytes(ASCII), false);   
      }

      i++;
    }

  }

}
