/*
 * Copyright 2014 Greg Kopff, 2021 Alwyn Schoeman
 *  All rights reserved.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */

package tech.nomads.raygun

internal class KeyMaster private constructor(private val keys: Map<String, String>) {

    fun apiKey(host: String) : String? =
        if (this.keys.containsKey(ANY_HOST))
            this.keys[ANY_HOST]
        else
            this.keys[host]

    companion object {
        private const val ANY_HOST: String = "__ANY_HOST"

        fun fromConfigString(config: String): KeyMaster =
            if (config.indexOf(' ') != -1)
                KeyMaster(this.parseNamed(config))
            else
                KeyMaster(mapOf(ANY_HOST to config))

        private fun parseNamed(encoded: String): Map<String, String> =
            encoded
                .splitToSequence(' ')
                .map { s ->
                    val pivot = s.indexOf(':')
                    if (pivot == -1) throw IllegalArgumentException("invalid format: $s")
                    s.substring(0, pivot) to s.substring(pivot + 1)
                }
                .toMap()
    }
}
