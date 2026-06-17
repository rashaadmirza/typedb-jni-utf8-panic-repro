import com.typedb.driver.TypeDB;
import com.typedb.driver.api.Credentials;
import com.typedb.driver.api.Driver;
import com.typedb.driver.api.DriverOptions;
import com.typedb.driver.api.DriverTlsConfig;
import com.typedb.driver.api.Transaction;

/**
 * Reproduction of a JNI driver crash on 4-byte UTF-8 characters.
 *
 * Environment:
 *   - TypeDB CE 3.11.5 (server), gRPC on localhost:1729, TLS disabled
 *   - com.typedb:typedb-driver:3.11.5 (Java driver)
 *   - JDK 21, macOS arm64
 *
 * Behavior:
 *   - Inserting an attribute value containing a 4-byte UTF-8 character
 *     (e.g. U+1F642 "🙂 slightly smiling face") aborts the JVM with SIGABRT
 *     (exit code 134) via a native Rust panic at the JNI boundary:
 *
 *       thread '<unnamed>' panicked at c/src/common/memory.rs:99:43:
 *       called `Result::unwrap()` on an `Err` value:
 *         Utf8Error { valid_up_to: 32, error_len: Some(1) }
 *       ...
 *       12: _transaction_query
 *       13: _Java_com_typedb_driver_jni_typedb_1driverJNI_transaction_1query
 *       thread caused non-unwinding panic. aborting.
 *
 *   - A 3-byte UTF-8 character (e.g. U+20AC € sign, or U+307E ま char)
 *     inserts successfully. Toggle the lines below to confirm the contrast.
 *
 * Notes:
 *   - The crash is at the JNI driver layer, not the server core: inserting the
 *     same 4-byte character through TypeDB Studio (gRPC/HTTP path) succeeds.
 *   - Reproduces identically on 3.10.3 (panic path was c/src/memory.rs:99).
 *
 * Start TypeDB 3.11.5 (no TLS):
 *   docker run -d --name typedb-jni-repro -p 1729:1729 typedb/typedb:3.11.5
 * 
 * Build and run:
 *   javac -cp "libs/*" EmojiPanicRepro.java && java  -cp "libs/*:." EmojiPanicRepro
 */
public class EmojiPanicRepro {

    private static final String ADDRESS = "http://localhost:1729"; // TypeDB server address, no TLS
    private static final String DB_NAME = "emoji_jni_repro";

    public static void main(String[] args) {
        DriverOptions options = new DriverOptions(DriverTlsConfig.disabled());

        try (Driver driver = TypeDB.driver(
                ADDRESS,
                new Credentials("admin", "password"), // default TypeDB credentials
                options)) {

            if (driver.databases().contains(DB_NAME)) {
                driver.databases().get(DB_NAME).delete();
            }
            driver.databases().create(DB_NAME);

            try (Transaction tx = driver.transaction(DB_NAME, Transaction.Type.SCHEMA)) {
                tx.query("define attribute label, value string; entity thing, owns label;").resolve();
                tx.commit();
            }

            try (Transaction tx = driver.transaction(DB_NAME, Transaction.Type.WRITE)) {
                // 4-byte UTF-8 char. Triggers the JNI panic / SIGABRT.
                tx.query("insert $t isa thing, has label \"\uD83D\uDE42\";").resolve(); // 🙂 4-byte CRASHES

                // Contrast cases (3-byte chars) that insert cleanly. Comment the line above and uncomment one of these to verify:
            //    tx.query("insert $t isa thing, has label \"\u20AC\";").resolve();  // € 3-byte
            //    tx.query("insert $t isa thing, has label \"\u307E\";").resolve();  // ま 3-byte

                tx.commit();
            }
        }

        System.out.println("No crash - insert succeeded.");
    }
}
