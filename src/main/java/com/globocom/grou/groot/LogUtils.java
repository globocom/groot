/*
 * Copyright (c) 2017-2018 Globo.com
 * All rights reserved.
 *
 * This source is subject to the Apache License, Version 2.0.
 * Please see the LICENSE file for more information.
 *
 * Authors: See AUTHORS file
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.globocom.grou.groot;

public class LogUtils {

    private final static boolean __escape = Boolean.parseBoolean(System.getProperty("om.globocom.grou.groot.logutils.ESCAPE","false"));

    private LogUtils() {
        //
    }

    public static String format(String msg, Object... args)
    {
        StringBuilder builder = new StringBuilder(64);
        if (msg == null)
        {
            StringBuilder msgBuilder = new StringBuilder();
            for (Object arg : args) {
                msgBuilder.append("{} ");
            }
            msg = msgBuilder.toString();
        }
        String braces = "{}";
        int start = 0;
        for (Object arg : args)
        {
            int bracesIndex = msg.indexOf(braces,start);
            if (bracesIndex < 0)
            {
                escape(builder,msg.substring(start));
                builder.append(" ");
                builder.append(arg);
                start = msg.length();
            }
            else
            {
                escape(builder,msg.substring(start,bracesIndex));
                builder.append(String.valueOf(arg));
                start = bracesIndex + braces.length();
            }
        }
        escape(builder,msg.substring(start));
        return builder.toString();
    }

    private static void escape(StringBuilder builder, String string)
    {
        if (__escape)
        {
            for (int i = 0; i < string.length(); ++i)
            {
                char c = string.charAt(i);
                if (Character.isISOControl(c))
                {
                    if (c == '\n')
                    {
                        builder.append('|');
                    }
                    else if (c == '\r')
                    {
                        builder.append('<');
                    }
                    else
                    {
                        builder.append('?');
                    }
                }
                else
                {
                    builder.append(c);
                }
            }
        }
        else
            builder.append(string);
    }
}
