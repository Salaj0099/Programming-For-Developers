package dashboard;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Background task that invents random stock quotes and puts them on a shared
 * queue. Runs on its own thread; never touches any Swing component.
 */
public class QuoteProducer implements Runnable {

    private static final String[] SYMBOLS = { "AAPL", "GOOG", "MSFT" };

    private final ArrayBlockingQueue<StockQuote> queue;
    private final Random random = new Random();

    /** Last price of each symbol, so prices drift instead of jumping randomly. */
    private final double[] lastPrices = { 190.0, 140.0, 420.0 };

    /** Written by the EDT (Stop button), read by the producer thread. */
    private volatile boolean running = true;

    /** Written by the EDT (speed slider), read by the producer thread. */
    private volatile int delayMs;

    /** Incremented by the producer thread, read by the EDT. */
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

    /** Asks the thread to finish its current loop and return. */
    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        while (running) {
            StockQuote quote = nextQuote();

            // offer() returns false instead of blocking when the queue is full.
            if (!queue.offer(quote)) {
                droppedCount.incrementAndGet();
            }

            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // restore the interrupt flag
                break;                              // and leave the loop
            }
        }
    }

    /** Produces the next random quote, drifting from the symbol's last price. */
    private StockQuote nextQuote() {
        int index = random.nextInt(SYMBOLS.length);

        double drift = (random.nextDouble() - 0.5) * 0.02; // between -1% and +1%
        double price = lastPrices[index] * (1 + drift);
        lastPrices[index] = price;

        double rounded = Math.round(price * 100.0) / 100.0;
        return new StockQuote(LocalDateTime.now(), SYMBOLS[index], rounded);
    }
}
