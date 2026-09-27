import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

val buildTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yy_HH-mm"))
println("Version: " + buildTime)
