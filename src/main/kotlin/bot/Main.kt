package bot

import org.apache.log4j.PropertyConfigurator
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
open class Main {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            PropertyConfigurator.configure("log4j.properties")
            SpringApplication.run(Main::class.java, *args)
        }
    }
}
