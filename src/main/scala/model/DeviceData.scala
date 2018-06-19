package model

import java.sql.Timestamp

case class DeviceData(device_id: Int, event_time: Timestamp, temperature: Double, humidity: Int)
