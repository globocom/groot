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

package com.globocom.grou.groot.jetty.listeners;

import java.util.concurrent.TimeUnit;

/**
 *
 */
public interface HistogramConstants
{

    long LOWEST_DISCERNIBLE_VALUE = TimeUnit.MILLISECONDS.toNanos( 1 );

    long HIGHEST_TRACKABLE_VALUE = TimeUnit.MINUTES.toNanos( 100000 );

    int NUMBER_OF_SIGNIFICANT_VALUE_DIGITS = 3;

}

