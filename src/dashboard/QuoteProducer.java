package dashboard;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class QuoteProducer implements Runnable {

    private static final String[] SYMBOLS = { "AAPL", "GOOG", "MSFT" };

    private final ArrayBlockingQueue<StockQuote> queue;
    private final Random random = new Random();

    private final double[] lastPrices = { 190.0, 140.0, 420.0 };

    private volatile boolean running = true;

    private volatile int delayMs;

    private final AtomicInteger droppedCount = new AtomicInteger(0);

    public QuoteProducer(ArrayBlockingQueue<StockQuote> queue, int delayMs) {
        this.queue = queue;
        this.delayMs = delayMs;
    }

    public void setDelayMs(int delayMs) {
        this.delayMs = delayMs;
    }

    public int getDroppedCount() {
        return droppedCount.get();
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        while (running) {
            StockQuote quote = nextQuote();

            if (!queue.offer(quote)) {
                droppedCount.incrementAndGet();
            }

            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private StockQuote nextQuote() {
        int index = random.nextInt(SYMBOLS.length);

        double drift = (random.nextDouble() - 0.5) * 0.02;
        double price = lastPrices[index] * (1 + drift);
        lastPrices[index] = price;

        double rounded = Math.round(price * 100.0) / 100.0;
        return new StockQuote(LocalDateTime.now(), SYMBOLS[index], rounded);
    }
}
