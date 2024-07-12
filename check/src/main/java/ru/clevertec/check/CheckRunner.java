package main.java.ru.clevertec.check;

import java.io.*;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CheckRunner {

    public static void main(String[] args) {
        System.out.println("Starting CheckRunner...");

        String pathToFile = null;
        String saveToFile = null;

        if (args.length == 0) {
            System.out.println("No arguments provided.");
            System.out.println("Please use this command on the command-line to run the program: 'java -cp src ./src/main/java/ru/clevertec/check/CheckRunner.java id-quantity discountCard=xxxx balanceDebitCard=xxxx pathToFile=xxxx saveToFile=xxxx'.");
            writeToResultCSV("ERROR\nBAD REQUEST\n", saveToFile != null ? saveToFile : "result.csv");
            System.out.println("Terminating CheckRunner due to bad request.");
            return;
        }

        String csvFileDiscountCards = "src/main/resources/discountCards.csv";
        String csvFileProducts = null;
        String line;
        String csvSplitBy = ";";

        String discountCardNumber = null;
        BigDecimal balanceDebitCard = null;

        StringBuilder resultBuilder = new StringBuilder();

        Map<Integer, Integer> requestedProducts = new LinkedHashMap<>();

        Pattern pattern = Pattern.compile("([^=]+)=([^=]+)");

        System.out.println("Parsing command-line arguments...");

        for (String arg : args) {
            if (arg.contains("-") && !arg.contains("=")) {
                String[] parts = arg.split("-");
                if (parts.length != 2) {
                    writeToResultCSV("ERROR\nBAD REQUEST\n", saveToFile != null ? saveToFile : "result.csv");
                    System.out.println("Terminating CheckRunner due to bad request.");
                    return;
                }
                try {
                    int id = Integer.parseInt(parts[0]);
                    int quantity = Integer.parseInt(parts[1]);
                    requestedProducts.merge(id, quantity, Integer::sum);
                } catch (NumberFormatException e) {
                    writeToResultCSV("ERROR\nBAD REQUEST\n", saveToFile != null ? saveToFile : "result.csv");
                    System.out.println("Terminating CheckRunner due to bad request.");
                    return;
                }
            } else {
                Matcher matcher = pattern.matcher(arg);
                if (matcher.find()) {
                    String key = matcher.group(1);
                    String value = matcher.group(2);
                    switch (key) {
                        case "discountCard" -> {
                            discountCardNumber = value;
                            if (discountCardNumber.length() != 4) {
                                writeToResultCSV("ERROR\nBAD REQUEST\n", saveToFile != null ? saveToFile : "result.csv");
                                System.out.println("Terminating CheckRunner due to bad request.");
                                return;
                            }
                        }
                        case "balanceDebitCard" -> {
                            try {
                                balanceDebitCard = BigDecimal.valueOf(Double.parseDouble(value));
                            } catch (NumberFormatException e) {
                                writeToResultCSV("ERROR\nBAD REQUEST\n", saveToFile != null ? saveToFile : "result.csv");
                                System.out.println("Terminating CheckRunner due to bad request.");
                                return;
                            }
                        }
                        case "pathToFile" -> pathToFile = value;
                        case "saveToFile" -> saveToFile = value;
                    }
                } else {
                    writeToResultCSV("ERROR\nBAD REQUEST\n", saveToFile != null ? saveToFile : "result.csv");
                    System.out.println("Terminating CheckRunner due to bad request.");
                    return;
                }
            }
        }

        if (pathToFile == null) {
            writeToResultCSV("ERROR\nBAD REQUEST\n", saveToFile != null ? saveToFile : "result.csv");
            System.out.println("Terminating CheckRunner due to missing pathToFile argument.");
            return;
        }

        csvFileProducts = pathToFile;

        System.out.println("Arguments parsed successfully.");

        ProductFactory productFactory = new ConcreteProductFactory();
        DiscountCardFactory discountCardFactory = new ConcreteDiscountCardFactory();

        Map<Integer, Product> products = new LinkedHashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(csvFileProducts))) {
            System.out.println("Reading products from file: " + csvFileProducts);
            br.readLine(); // Skip header line

            while ((line = br.readLine()) != null) {
                String[] values = line.split(csvSplitBy);
                int id = Integer.parseInt(values[0]);
                String description = values[1];
                double price = Double.parseDouble(values[2]);
                int quantityInStock = Integer.parseInt(values[3]);
                boolean wholesaleProduct = Boolean.parseBoolean(values[4]);

                Product product = productFactory.createProduct(id, description, price, quantityInStock, wholesaleProduct);
                products.put(id, product);
            }
            System.out.println("Products read successfully.");
        } catch (IOException e) {
            writeToResultCSV("ERROR\nINTERNAL SERVER ERROR\n", saveToFile != null ? saveToFile : "result.csv");
            System.out.println("Terminating CheckRunner due to internal server error while reading products.");
            e.printStackTrace();
            return;
        }

        Map<String, DiscountCard> discountCards = new LinkedHashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(csvFileDiscountCards))) {
            System.out.println("Reading discount cards from file: " + csvFileDiscountCards);
            br.readLine(); // Skip header line

            while ((line = br.readLine()) != null) {
                String[] values = line.split(csvSplitBy);
                String cardNumber = values[1];
                int discountAmount = Integer.parseInt(values[2]);

                DiscountCard discountCard = discountCardFactory.createDiscountCard(cardNumber, discountAmount);
                discountCards.put(cardNumber, discountCard);
            }
            System.out.println("Discount cards read successfully.");
        } catch (IOException e) {
            writeToResultCSV("ERROR\nINTERNAL SERVER ERROR\n", saveToFile != null ? saveToFile : "result.csv");
            System.out.println("Terminating CheckRunner due to internal server error while reading discount cards.");
            e.printStackTrace();
            return;
        }

        int totalDiscount = 0;
        if (discountCardNumber != null) {
            totalDiscount = 2;
            if (discountCards.containsKey(discountCardNumber)) {
                totalDiscount = discountCards.get(discountCardNumber).getDiscountPercentage();
            }
        }

        BigDecimal totalSum = BigDecimal.ZERO;
        BigDecimal totalDiscountAmount = BigDecimal.ZERO;

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
        Date now = new Date();
        String currentDate = dateFormat.format(now);
        String currentTime = timeFormat.format(now);

        CheckBuilder checkBuilder = new CheckBuilder();
        checkBuilder.addDateAndTime(currentDate, currentTime);

        System.out.println("Calculating order details...");

        try {
            for (Map.Entry<Integer, Integer> entry : requestedProducts.entrySet()) {
                int productId = entry.getKey();
                int requestedQuantity = entry.getValue();

                Product product = products.get(productId);
                if (product != null) {
                    if (requestedQuantity <= product.getQuantity()) {
                        BigDecimal price = roundMoneyHalfUp(BigDecimal.valueOf(product.getPrice()));
                        BigDecimal discount;

                        DiscountStrategy discountStrategy = product.isWholesaleProduct() && requestedQuantity >= 5
                                ? new WholesaleDiscountStrategy()
                                : new RegularDiscountStrategy();

                        discount = discountStrategy.calculateDiscount(product, requestedQuantity, totalDiscount);

                        BigDecimal total = roundMoneyHalfUp(price.multiply(BigDecimal.valueOf(requestedQuantity)).subtract(discount));

                        totalSum = roundMoneyHalfUp(totalSum.add(total));
                        totalDiscountAmount = roundMoneyHalfUp(totalDiscountAmount.add(discount));

                        if (balanceDebitCard != null && balanceDebitCard.compareTo(totalSum) < 0) {
                            writeToResultCSV("ERROR\nNOT ENOUGH MONEY\n", saveToFile != null ? saveToFile : "result.csv");
                            System.out.println("Terminating CheckRunner due to not enough balance.");
                            return;
                        }

                        checkBuilder.addProductDetails(requestedQuantity, product.getName(), price, discount, total);

                        System.out.println("Added product " + product.getName() + " (ID: " + productId + ") to order: " + requestedQuantity + " units.");
                    } else {
                        writeToResultCSV("ERROR\nBAD REQUEST\n", saveToFile != null ? saveToFile : "result.csv");
                        System.out.println("Terminating CheckRunner due to requested quantity exceeding available stock for product with ID " + productId + ". Available quantity: " + product.getQuantity());
                        return;
                    }
                } else {
                    writeToResultCSV("ERROR\nBAD REQUEST\n", saveToFile != null ? saveToFile : "result.csv");
                    System.out.println("Terminating CheckRunner due to product with ID " + productId + " not found.");
                    return;
                }
            }
        } catch (NullPointerException e) {
            writeToResultCSV("ERROR\nBAD REQUEST\n", saveToFile != null ? saveToFile : "result.csv");
            System.out.println("Terminating CheckRunner due to bad request.");
            return;
        }

        System.out.println("Order calculation completed successfully.");

        if (discountCardNumber != null) {
            checkBuilder.addDiscountCardDetails(discountCardNumber, totalDiscount);
        }

        checkBuilder.addTotal(totalSum.add(totalDiscountAmount), totalDiscountAmount, totalSum);

        System.out.println("Finalizing order details...");

        if (saveToFile == null) {
            writeToResultCSV("ERROR\nBAD REQUEST\n", "result.csv");
            System.out.println("Terminating CheckRunner due to missing saveToFile argument.");
            return;
        }

        writeToResultCSV(checkBuilder.build(), saveToFile);

        System.out.println("Order details written to " + saveToFile);
        System.out.println("CheckRunner execution completed successfully.");
    }

    private static void writeToResultCSV(String content, String fileName) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            writer.write(content);
            System.out.println("Result written to " + fileName + ":\n");
            System.out.println(content);
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Failed to write result to " + fileName);
        }
    }

    @SuppressWarnings("deprecation")
    private static BigDecimal roundMoneyHalfUp(BigDecimal value) {
        return value.setScale(2, 4);
    }
}

class Product {
    final private int id;
    final private String description;
    final private double price;
    final private int quantityInStock;
    final private boolean wholesaleProduct;

    public Product(int id, String description, double price, int quantityInStock, boolean wholesaleProduct) {
        this.id = id;
        this.description = description;
        this.price = price;
        this.quantityInStock = quantityInStock;
        this.wholesaleProduct = wholesaleProduct;
    }

    public String getName() {
        return description;
    }

    public double getPrice() {
        return price;
    }

    public int getQuantity() {
        return quantityInStock;
    }

    public boolean isWholesaleProduct() {
        return wholesaleProduct;
    }

    @Override
    public String toString() {
        return String.format("{id=%d, description='%s', price=" + price + ", quantity_in_stock=%d, wholesale_product='%s'}", id, description, quantityInStock, wholesaleProduct);
    }
}

interface DiscountCardFactory {
    DiscountCard createDiscountCard(String cardNumber, int discountPercentage);
}

class ConcreteDiscountCardFactory implements DiscountCardFactory {
    @Override
    public DiscountCard createDiscountCard(String cardNumber, int discountPercentage) {
        return new DiscountCard(cardNumber, discountPercentage);
    }
}

class DiscountCard {
    private final String cardNumber;
    private final int discountPercentage;

    public DiscountCard(String cardNumber, int discountPercentage) {
        this.cardNumber = cardNumber;
        this.discountPercentage = discountPercentage;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public int getDiscountPercentage() {
        return discountPercentage;
    }
}

interface ProductFactory {
    Product createProduct(int id, String description, double price, int quantityInStock, boolean wholesaleProduct);
}

class ConcreteProductFactory implements ProductFactory {
    @Override
    public Product createProduct(int id, String description, double price, int quantityInStock, boolean wholesaleProduct) {
        return new Product(id, description, price, quantityInStock, wholesaleProduct);
    }
}

class CheckBuilder {
    private StringBuilder result;

    public CheckBuilder() {
        result = new StringBuilder();
    }

    public CheckBuilder addDateAndTime(String date, String time) {
        result.append("Date;Time\n");
        result.append(date).append(";").append(time).append("\n\n");
        return this;
    }

    public CheckBuilder addProductDetails(int quantity, String description, BigDecimal price, BigDecimal discount, BigDecimal total) {
        result.append(quantity).append(";").append(description).append(";")
                .append(price).append("$;").append(discount).append("$;")
                .append(total).append("$\n");
        return this;
    }

    public CheckBuilder addDiscountCardDetails(String discountCardNumber, int discountPercentage) {
        result.append("\nDISCOUNT CARD;DISCOUNT PERCENTAGE\n")
                .append(discountCardNumber).append(";").append(discountPercentage).append("%\n");
        return this;
    }

    public CheckBuilder addTotal(BigDecimal totalSum, BigDecimal totalDiscountAmount, BigDecimal totalWithDiscount) {
        result.append("\nTOTAL PRICE;TOTAL DISCOUNT;TOTAL WITH DISCOUNT\n")
                .append(totalSum).append("$;").append(totalDiscountAmount).append("$;")
                .append(totalWithDiscount).append("$\n");
        return this;
    }

    public String build() {
        return result.toString();
    }
}

interface DiscountStrategy {
    BigDecimal calculateDiscount(Product product, int quantity, int totalDiscount);
}

class RegularDiscountStrategy implements DiscountStrategy {
    @Override
    public BigDecimal calculateDiscount(Product product, int quantity, int totalDiscount) {
        return roundMoneyHalfUp(
                BigDecimal.valueOf(product.getPrice()).multiply(BigDecimal.valueOf(totalDiscount))
                        .multiply(BigDecimal.valueOf(quantity)).divide(BigDecimal.valueOf(100))
        );
    }

    @SuppressWarnings("deprecation")
    private static BigDecimal roundMoneyHalfUp(BigDecimal value) {
        return value.setScale(2, 4);
    }
}

class WholesaleDiscountStrategy implements DiscountStrategy {
    @Override
    public BigDecimal calculateDiscount(Product product, int quantity, int totalDiscount) {
        return roundMoneyHalfUp(
                BigDecimal.valueOf(product.getPrice()).multiply(BigDecimal.valueOf(0.10))
                        .multiply(BigDecimal.valueOf(quantity))
        );
    }

    @SuppressWarnings("deprecation")
    private static BigDecimal roundMoneyHalfUp(BigDecimal value) {
        return value.setScale(2, 4);
    }
}
