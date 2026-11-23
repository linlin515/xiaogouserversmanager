package com.xiaogou.serverseyes

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.xiaogou.serverseyes.data.local.AppDatabase
import com.xiaogou.serverseyes.data.local.MetricSampleDao
import com.xiaogou.serverseyes.data.local.MetricSampleEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MetricSampleDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: MetricSampleDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.metricSampleDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun bucketQueryAveragesWithinTimeWindows() = runBlocking {
        val base = 1_000_000L
        dao.insertAllIgnore(
            listOf(
                sample(base + 1_000L, cpu = 10.0, mem = 20.0, temp = 40.0),
                sample(base + 5_000L, cpu = 30.0, mem = 40.0, temp = 50.0),
                sample(base + 12_000L, cpu = 60.0, mem = 60.0, temp = null),
                sample(base + 19_000L, cpu = 80.0, mem = 80.0, temp = 70.0)
            )
        )

        val rows = dao.getRangeBucketed("s1", base, 10_000L)

        assertEquals(2, rows.size)
        assertEquals(base, rows[0].bucketStartMs)
        assertEquals(20.0, rows[0].avgCpuPercent, 0.001)
        assertEquals(30.0, rows[0].avgMemPercent, 0.001)
        assertEquals(45.0, rows[0].avgCpuTempCelsius ?: 0.0, 0.001)
        assertEquals(50.0, rows[0].maxCpuTempCelsius ?: 0.0, 0.001)

        assertEquals(base + 10_000L, rows[1].bucketStartMs)
        assertEquals(70.0, rows[1].avgCpuPercent, 0.001)
        assertEquals(80.0, rows[1].maxCpuPercent, 0.001)
        // 空温度不参与 AVG，但同一桶内有效温度仍可聚合
        assertEquals(70.0, rows[1].avgCpuTempCelsius ?: 0.0, 0.001)
    }

    @Test
    fun bucketQueryRespectsSinceAndServerFilter() = runBlocking {
        dao.insertAllIgnore(
            listOf(
                sample(1_000L, cpu = 10.0, mem = 10.0, temp = null, serverId = "s1"),
                sample(2_000L, cpu = 20.0, mem = 20.0, temp = null, serverId = "s1"),
                sample(3_000L, cpu = 30.0, mem = 30.0, temp = null, serverId = "s2")
            )
        )

        val rows = dao.getRangeBucketed("s1", sinceMs = 1_500L, bucketMs = 10_000L)

        assertEquals(1, rows.size)
        assertEquals(20.0, rows[0].avgCpuPercent, 0.001)
    }

    private fun sample(
        ts: Long,
        cpu: Double,
        mem: Double,
        temp: Double?,
        serverId: String = "s1"
    ) = MetricSampleEntity(
        serverId = serverId,
        timestampMs = ts,
        cpuPercent = cpu,
        memPercent = mem,
        diskMaxPercent = 50.0,
        netRxBytesPerSec = 1_024L,
        netTxBytesPerSec = 512L,
        load1 = 1.0,
        sourceUsed = "SSH",
        cpuTempCelsius = temp
    )
}
