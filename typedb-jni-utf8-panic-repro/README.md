# TypeDB JNI Driver — 4-byte UTF-8 Crash Reproduction

Reproduces a native driver crash (SIGABRT, exit 134) when inserting a 4-byte UTF-8 character via the TypeDB Java driver over JNI.

## The Bug

Inserting an attribute string containing a 4-byte UTF-8 character (e.g. U+1F642 🙂) aborts the JVM with a Rust panic at the JNI boundary:

```
thread '<unnamed>' panicked at c/src/common/memory.rs:99:43:
called `Result::unwrap()` on an `Err` value: Utf8Error { valid_up_to: 32, error_len: Some(1) }
...
12: _transaction_query
13: _Java_com_typedb_driver_jni_typedb_1driverJNI_transaction_1query
thread caused non-unwinding panic. aborting.
```

- **Confirmed on:** TypeDB 3.10.3 and 3.11.5
- **Path:** JNI driver only — inserting the same character via TypeDB Studio (gRPC) succeeds
- **Trigger:** Any 4-byte UTF-8 character (most emoji, e.g. 🙂 U+1F642)
- **Safe:** 3-byte UTF-8 characters (e.g. € U+20AC, ま U+307E) insert cleanly

## Files

```
typedb-jni-utf8-panic-repro/
├── EmojiPanicRepro.java
├── README.md
└── libs/
    ├── typedb-driver-3.11.5.jar
    └── typedb-driver-jni-macosx-arm64-3.11.5.jar
```

## Prerequisites

- JDK 21+
- Docker

## Setup

### 1. Start TypeDB 3.11.5

```bash
docker run -d --name typedb-jni-repro -p 1729:1729 typedb/typedb:3.11.5
```

Wait ~10 seconds for the server to be ready.

### 2. Build

```bash
javac -cp "libs/*" EmojiPanicRepro.java
```

### 3. Run

```bash
java -cp "libs/*:." EmojiPanicRepro
```

## Expected Output

### Crash case (4-byte emoji, active by default)

```
thread '<unnamed>' panicked at c/src/common/memory.rs:99:43:
called `Result::unwrap()` on an `Err` value: Utf8Error { valid_up_to: 32, error_len: Some(1) }
...
thread caused non-unwinding panic. aborting.
zsh: abort      java -cp "libs/*:." EmojiPanicRepro
```

Exit code: 134 (SIGABRT)

### Contrast case (3-byte character)

Comment out the 4-byte line and uncomment one of the 3-byte lines in `EmojiPanicRepro.java`:

```java
// tx.query("insert $t isa thing, has label \"\uD83D\uDE42\";").resolve(); // 🙂 4-byte CRASHES
tx.query("insert $t isa thing, has label \"\u20AC\";").resolve();          // € 3-byte - works
```

Recompile and run:

```bash
javac -cp "libs/*" EmojiPanicRepro.java && java -cp "libs/*:." EmojiPanicRepro
```

Expected output:

```
No crash - insert succeeded.
```

## Notes

- The `libs/` folder contains the TypeDB Java driver 3.11.5 jars for macOS arm64.
- If you are on a different platform, replace `typedb-driver-jni-macosx-arm64-3.11.5.jar` with the appropriate JNI jar for your OS:
  - macOS x86_64: `typedb-driver-jni-macosx-x86_64-3.11.5.jar`
  - Linux x86_64: `typedb-driver-jni-linux-x86_64-3.11.5.jar`
  - Linux arm64: `typedb-driver-jni-linux-arm64-3.11.5.jar`
  - Windows x86_64: `typedb-driver-jni-windows-x86_64-3.11.5.jar`