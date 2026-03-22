/*
MIT License

Copyright (c) 2022 WorkOS

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

package dev.mikepenz.composebuddy.inspector.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val RadixChevronDown: ImageVector
    get() {
        if (_RadixChevronDown != null) return _RadixChevronDown!!

        _RadixChevronDown = ImageVector.Builder(
            name = "chevron-down",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 15f,
            viewportHeight = 15f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
            ) {
                moveTo(11.1581f, 6.13499f)
                curveTo(11.3595f, 5.94644f, 11.6763f, 5.95708f, 11.8651f, 6.15843f)
                curveTo(12.0536f, 6.3599f, 12.043f, 6.67669f, 11.8416f, 6.86546f)
                lineTo(7.84165f, 10.6155f)
                curveTo(7.64941f, 10.7954f, 7.35029f, 10.7954f, 7.15805f, 10.6155f)
                lineTo(3.15805f, 6.86546f)
                lineTo(3.09165f, 6.78831f)
                curveTo(2.95746f, 6.59859f, 2.96967f, 6.33468f, 3.13461f, 6.15843f)
                curveTo(3.29985f, 5.98217f, 3.56347f, 5.95169f, 3.76157f, 6.07347f)
                lineTo(3.84165f, 6.13499f)
                lineTo(7.49985f, 9.5637f)
                lineTo(11.1581f, 6.13499f)
                close()
            }
        }.build()

        return _RadixChevronDown!!
    }

private var _RadixChevronDown: ImageVector? = null
