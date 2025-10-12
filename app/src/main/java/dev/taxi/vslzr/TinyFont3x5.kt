package dev.taxi.vslzr

class TinyFont3x5 {
    // 3x5 digits + ':', '%', and "FULL"
    private val DIGITS = arrayOf(
        arrayOf("111","101","101","101","111"), //0
        arrayOf("010","110","010","010","111"), //1
        arrayOf("111","001","111","100","111"), //2
        arrayOf("111","001","111","001","111"), //3
        arrayOf("101","101","111","001","001"), //4
        arrayOf("111","100","111","001","111"), //5
        arrayOf("111","100","111","101","111"), //6
        arrayOf("111","001","001","001","001"), //7
        arrayOf("111","101","111","101","111"), //8
        arrayOf("111","101","111","001","111")  //9
    )
    private val COLON = booleanArrayOf(false,true,false,true,false)
    private val PCT = arrayOf("110","001","010","100","111") // crude '%'
    private val FULL = arrayOf( // 4 letters, 3x5 + 1px spacing each
        arrayOf("111","100","110","100","100"), // F
        arrayOf("101","101","101","101","111"), // U
        arrayOf("100","100","100","100","111"), // L
        arrayOf("100","100","100","100","111")  // L
    )

    fun drawDigit(buf:Array<IntArray>, d:Int, x:Int, y:Int, bright:Int) {
        val g = DIGITS[d]
        for (r in 0 until 5) for (c in 0 until 3)
            if (g[r][c]=='1') set(buf, x+c, y+r, bright)
    }
    fun drawColon(buf:Array<IntArray>, x:Int, y:Int, bright:Int) {
        if (y+1<=24) set(buf, x, y+1, bright)
        if (y+3<=24) set(buf, x, y+3, bright)
    }
    fun drawPercent(buf:Array<IntArray>, x:Int, y:Int, bright:Int) {
        for (r in 0 until 5) for (c in 0 until 3)
            if (PCT[r][c]=='1') set(buf, x+c, y+r, bright)
    }
    fun drawTextFULL(buf:Array<IntArray>, x:Int, y:Int, bright:Int) {
        var cx = x
        for (ch in 0 until 4) {
            val g = FULL[ch]
            for (r in 0 until 5) for (c in 0 until 3)
                if (g[r][c]=='1') set(buf, cx+c, y+r, bright)
            cx += 4
        }
    }
    private fun set(buf:Array<IntArray>, x:Int, y:Int, v:Int) {
        if (x in 0..24 && y in 0..24) buf[y][x] = maxOf(buf[y][x], v)
    }
}
