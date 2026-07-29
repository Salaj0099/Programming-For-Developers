package dashboard;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class StockQuote {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    private final LocalDateTime timestamp;
    private final String symbol;
    private final double price;

    public StockQuote(LocalDateTime timestamp, String symbol, double price) {
        this.timestamp = timestamp;
        this.symbol = symbol;
        this.price = price;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getSymbol() {
        return symbol;
    }

    public double getPrice() {
        return price;
    }

    public String getFormattedTime() {
        return timestamp.format(TIME_FORMAT);
    }

    @Override
    public String toString() {
        return String.format("%s %s %.2f", getFormattedTime(), symbol, price);
    }
}
