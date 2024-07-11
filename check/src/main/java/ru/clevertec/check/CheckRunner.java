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

        if (args.length == 0) {
            System.out.println("No arguments provided.");
            System.out.println("Please use this command on the command-line to run the program: 'java -cp src ./src/main/java/ru/clevertec/check/CheckRunner.java id-quantity discountCard=xxxx balanceDebitCard=xxxx'.");
            System.out.println("Where: id - product number; quantity - quantity of product; discountCard - discount card number; balanceDebitCard - card balance.");
            writeToResultCSV("ERROR\nBAD REQUEST\n");
            System.out.println("Terminating CheckRunner due to bad request.");
            return;
        }

        String csvFileDiscountCards = "src/main/resources/discountCards.csv";
        String csvFileProducts = "src/main/resources/products.csv";
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
                    writeToResultCSV("ERROR\nBAD REQUEST\n");
                    System.out.println("Terminating CheckRunner due to bad request.");
                    return;
                }
                try {
                    int id = Integer.parseInt(parts[0]);
                    int quantity = Integer.parseInt(parts[1]);
                    requestedProducts.merge(id, quantity, Integer::sum);
                } catch (NumberFormatException e) {
                    writeToResultCSV("ERROR\nBAD REQUEST\n");
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
                                writeToResultCSV("ERROR\nBAD REQUEST\n");
                                System.out.println("Terminating CheckRunner due to bad request.");
                                return;
                            }
                        }
                        case "balanceDebitCard" -> balanceDebitCard = BigDecimal.valueOf(Double.parseDouble(value));
                    }
                } else {
                    writeToResultCSV("ERROR\nBAD REQUEST\n");
                    System.out.println("Terminating CheckRunner due to bad request.");
                    return;
                }
            }
        }

        System.out.println("Arguments parsed successfully.");

        Map<Integer, Product> products = new LinkedHashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(csvFileProducts))) {
            System.out.println("Reading products from file: " + csvFileProducts);
            br.readLine();

            while ((line = br.readLine()) != null) {
                String[] values = line.split(csvSplitBy);
                int id = Integer.parseInt(values[0]);
                String description = values[1];
                double price = Double.parseDouble(values[2]);
                int quantityInStock = Integer.parseInt(values[3]);
                boolean wholesaleProduct = Boolean.parseBoolean(values[4]);

                Product product = new Product(id, description, price, quantityInStock, wholesaleProduct);
                products.put(id, product);
            }
            System.out.println("Products read successfully.");
        } catch (IOException e) {
            writeToResultCSV("ERROR\nINTERNAL SERVER ERROR\n");
            System.out.println("Terminating CheckRunner due to internal server error while reading products.");
            e.printStackTrace();
            return;
        }

        Map<String, Integer> discountCards = new LinkedHashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(csvFileDiscountCards))) {
            System.out.println("Reading discount cards from file: " + csvFileDiscountCards);
            br.readLine();

            while ((line = br.readLine()) != null) {
                String[] values = line.split(csvSplitBy);
                String cardNumber = values[1];
                int discountAmount = Integer.parseInt(values[2]);

                discountCards.put(cardNumber, discountAmount);
            }
            System.out.println("Discount cards read successfully.");
        } catch (IOException e) {
            writeToResultCSV("ERROR\nINTERNAL SERVER ERROR\n");
            System.out.println("Terminating CheckRunner due to internal server error while reading discount cards.");
            e.printStackTrace();
            return;
        }

        int totalDiscount = 0;
        if (discountCardNumber != null) {
            totalDiscount = 2;
            if (discountCards.containsKey(discountCardNumber)) {
                totalDiscount = discountCards.get(discountCardNumber);
            }
        }

        BigDecimal totalSum = BigDecimal.ZERO;
        BigDecimal totalDiscountAmount = BigDecimal.ZERO;

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
        Date now = new Date();
        String currentDate = dateFormat.format(now);
        String currentTime = timeFormat.format(now);

        resultBuilder.append("Date;Time\n");
        resultBuilder.append(currentDate).append(";").append(currentTime).append("\n\n");

        resultBuilder.append("QTY;DESCRIPTION;PRICE;DISCOUNT;TOTAL\n");

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

                        if (product.isWholesaleProduct() && requestedQuantity >= 5) {
                            discount = roundMoneyHalfUp(price.multiply(BigDecimal.valueOf(0.10)).multiply(BigDecimal.valueOf(requestedQuantity)));
                        } else {
                            discount = roundMoneyHalfUp(price.multiply(BigDecimal.valueOf(totalDiscount)).multiply(BigDecimal.valueOf(requestedQuantity)).divide(BigDecimal.valueOf(100)));
                        }

                        BigDecimal total = roundMoneyHalfUp(price.multiply(BigDecimal.valueOf(requestedQuantity)).subtract(discount));

                        totalSum = roundMoneyHalfUp(totalSum.add(total));
                        totalDiscountAmount = roundMoneyHalfUp(totalDiscountAmount.add(discount));

                        if (balanceDebitCard.compareTo(totalSum) < 0) {
                            writeToResultCSV("ERROR\nNOT ENOUGH MONEY\n");
                            System.out.println("Terminating CheckRunner due to not enough balance.");
                            return;
                        }

                        if (product.isWholesaleProduct() && requestedQuantity >= 5) {
                            resultBuilder.append(requestedQuantity).append(";").append(product.getName()).append(";").append(price).append("$;").append(discount).append("$;").append(total).append("$\n");
                        } else {
                            resultBuilder.append(requestedQuantity).append(";").append(product.getName()).append(";").append(price).append("$;").append(discount).append("$;").append(total).append("$\n");
                        }

                        System.out.println("Added product " + product.getName() + " (ID: " + productId + ") to order: " + requestedQuantity + " units.");
                    } else {
                        writeToResultCSV("ERROR\nBAD REQUEST\n");
                        System.out.println("Terminating CheckRunner due to requested quantity exceeding available stock for product with ID " + productId + ". Available quantity: " + product.getQuantity());
                        return;
                    }
                } else {
                    writeToResultCSV("ERROR\nBAD REQUEST\n");
                    System.out.println("Terminating CheckRunner due to product with ID " + productId + " not found.");
                    return;
                }
            }
        } catch (NullPointerException e) {
            writeToResultCSV("ERROR\nBAD REQUEST\n");
            System.out.println("Terminating CheckRunner due to bad request.");
            return;
        }

        System.out.println("Order calculation completed successfully.");

        if (discountCardNumber != null) {
            resultBuilder.append("\nDISCOUNT CARD;DISCOUNT PERCENTAGE\n");
            resultBuilder.append(discountCardNumber).append(";").append(totalDiscount).append("%\n");
        }

        String formattedTotalSum = totalSum.add(totalDiscountAmount).toString();
        String formattedTotalDiscountAmount = totalDiscountAmount.toString();
        String formattedTotalWithDiscount = totalSum.toString();

        resultBuilder.append("\nTOTAL PRICE;TOTAL DISCOUNT;TOTAL WITH DISCOUNT\n");
        resultBuilder.append(formattedTotalSum).append("$;").append(formattedTotalDiscountAmount).append("$;").append(formattedTotalWithDiscount).append("$\n");

        System.out.println("Finalizing order details...");

        writeToResultCSV(resultBuilder.toString());

        System.out.println("Order details written to result.csv.");
        System.out.println("CheckRunner execution completed successfully.");
    }

    private static void writeToResultCSV(String content) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("./result.csv"))) {
            writer.write(content);
            System.out.println("Result written to result.csv:\n");
            System.out.println(content);
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Failed to write result to result.csv.");
        }
    }

    @SuppressWarnings("deprecation")
    private static BigDecimal roundMoneyHalfUp(BigDecimal value) {
        return value.setScale(2, BigDecimal.ROUND_HALF_UP);
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
