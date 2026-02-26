package com.ht.batterytx

import java.nio.ByteBuffer
import java.nio.ByteOrder

object PayloadBuilder {
    const val MANUFACTURER_ID = 0xFFFF
    const val MAGIC = 0xAABB

    fun build(tabletId: Int, data: BatteryData, seq: Int): ByteArray {
        val buffer = ByteBuffer.allocate(11).order(ByteOrder.LITTLE_ENDIAN)
        val safeTabletId = tabletId and 0xFFFF
        val safePercent = data.percent.coerceIn(0, 100)
        val flags = (if (data.charging) 1 else 0) or
            (if (data.full) 2 else 0) or
            (if (data.plugged) 4 else 0)
        val safeTemp = data.temperatureC_x10.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        val safeVoltage = data.voltageMv.coerceIn(0, 0xFFFF)
        val safeSeq = seq and 0xFF

        buffer.putShort(MAGIC.toShort())
        buffer.putShort(safeTabletId.toShort())
        buffer.put(safePercent.toByte())
        buffer.put(flags.toByte())
        buffer.putShort(safeTemp.toShort())
        buffer.putShort(safeVoltage.toShort())
        buffer.put(safeSeq.toByte())
        return buffer.array()
    }
}
