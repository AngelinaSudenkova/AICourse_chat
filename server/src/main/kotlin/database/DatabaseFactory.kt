package database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URI

class DatabaseFactory {
    private val databaseUrl = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/kmp"
    private val dbUser = System.getenv("DB_USER") ?: "postgres"
    private val dbPassword = System.getenv("DB_PASSWORD") ?: "postgres"
    
    fun init() {
        try {
            val uri = URI(databaseUrl.replace("jdbc:", ""))
            val host = uri.host
            val port = if (uri.port == -1) 5432 else uri.port
            val dbName = uri.path.removePrefix("/")
            
            val config = HikariConfig().apply {
                jdbcUrl = "jdbc:postgresql://$host:$port/$dbName"
                username = dbUser
                password = dbPassword
                driverClassName = "org.postgresql.Driver"
                maximumPoolSize = 10
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            }
            
            val dataSource = HikariDataSource(config)
            Database.connect(dataSource)
            
            val flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
            
            flyway.migrate()
        } catch (e: Exception) {
            throw IllegalStateException(
                "Failed to connect to database at $databaseUrl. " +
                "Please ensure PostgreSQL is running. " +
                "Start it with: docker compose up -d",
                e
            )
        }
    }
}

