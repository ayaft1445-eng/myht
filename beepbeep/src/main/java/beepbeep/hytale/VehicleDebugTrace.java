package beepbeep.hytale;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class VehicleDebugTrace {
   static final int MAX_EVENTS = 20000;
   static final long MAX_NANOS = 90000000000L;
   private static final Gson JSON = new GsonBuilder().serializeNulls().create();
   private static final Set<VehicleDebugTrace> ACTIVE = ConcurrentHashMap.newKeySet();
   private static final Set<CompletableFuture<Void>> PENDING = ConcurrentHashMap.newKeySet();
   private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(var0 -> {
      Thread var1 = new Thread(var0, "beepbeep-debug-writer");
      var1.setDaemon(true);
      return var1;
   });
   final Path path;
   final long started = System.nanoTime();
   private final int capacity;
   private final ArrayList<JsonObject> events = new ArrayList<>();
   private long sequence;
   private long dropped;
   private volatile boolean active = true;
   private volatile String status = "recording";
   private volatile CompletableFuture<Void> saved;

   VehicleDebugTrace(Path var1) {
      this(var1, 20000);
   }

   VehicleDebugTrace(Path var1, int var2) {
      this.capacity = var2;
      this.path = var1.resolve("trace-" + Instant.now().toString().replace(':', '-') + "-" + UUID.randomUUID().toString().substring(0, 8) + ".jsonl");
      ACTIVE.add(this);
      JsonObject var3 = new JsonObject();
      var3.addProperty("schema", 1);
      var3.addProperty("build", "0.3.24");
      var3.addProperty("startedUtc", Instant.now().toString());
      var3.addProperty("sampleHz", 10);
      var3.addProperty("durationLimitSeconds", 90);
      var3.addProperty("maxEvents", var2);
      var3.addProperty("rawKeyboardAvailable", false);
      var3.addProperty("scope", "mounted client packets, decoded input, server ECS phases and outgoing mod commands; no OS keys or rendered client pose");
      this.event("header", var3);
   }

   boolean active() {
      return this.active;
   }

   boolean expired() {
      return System.nanoTime() - this.started >= 90000000000L;
   }

   synchronized void event(String var1, JsonObject var2) {
      if (this.active) {
         if (this.events.size() >= this.capacity) {
            this.dropped++;
         } else {
            JsonObject var3 = var2.deepCopy();
            var3.addProperty("type", var1);
            var3.addProperty("seq", ++this.sequence);
            var3.addProperty("t", (double)(System.nanoTime() - this.started) / 1.0E9);
            this.events.add(var3);
         }
      }
   }

   void event(String var1, String var2) {
      JsonObject var3 = new JsonObject();
      var3.addProperty("detail", var2);
      this.event(var1, var3);
   }

   synchronized String status() {
      return this.status + " events=" + this.events.size() + " dropped=" + this.dropped + " file=" + this.path;
   }

   synchronized CompletableFuture<Void> stop(String var1) {
      if (!this.active) {
         return this.saved == null ? CompletableFuture.completedFuture(null) : this.saved;
      } else {
         JsonObject var2 = new JsonObject();
         var2.addProperty("reason", var1);
         var2.addProperty("dropped", this.dropped);
         var2.addProperty("type", "end");
         var2.addProperty("seq", ++this.sequence);
         var2.addProperty("t", (double)(System.nanoTime() - this.started) / 1.0E9);
         this.events.add(var2);
         this.active = false;
         ACTIVE.remove(this);
         this.status = "saving";
         ArrayList var3 = new ArrayList<>(this.events);
         this.saved = CompletableFuture.runAsync(() -> {
            try {
               Files.createDirectories(this.path.getParent());

               try (BufferedWriter var2x = Files.newBufferedWriter(this.path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
                  for (JsonObject var4 : var3) {
                     var2x.write(JSON.toJson(var4));
                     var2x.newLine();
                  }
               }

               this.status = "saved";
               System.out.println("[BeepBeep debug] saved " + this.path + " events=" + var3.size());
            } catch (IOException var7) {
               this.status = "write failed: " + var7.getMessage();
               throw new CompletionException(var7);
            }
         }, WRITER);
         PENDING.add(this.saved);
         this.saved.whenComplete((var1x, var2x) -> PENDING.remove(this.saved));
         return this.saved;
      }
   }

   static void shutdown() {
      for (VehicleDebugTrace var3 : ACTIVE.toArray(VehicleDebugTrace[]::new)) {
         var3.stop("plugin-shutdown");
      }

      CompletableFuture[] var5 = PENDING.toArray(CompletableFuture[]::new);

      try {
         CompletableFuture.allOf(var5).get(5L, TimeUnit.SECONDS);
      } catch (Exception var4) {
         System.err.println("[BeepBeep debug] could not finish all recordings: " + var4);
      }
   }
}
