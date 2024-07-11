# Check

CheckRunner is a program designed to process purchases, apply discounts, and calculate the total order cost considering the balance on a debit card.

## Description

The program reads product and discount card data from CSV files, accepts command-line arguments describing the purchase (product IDs and quantities, discount card number, debit card balance), and then calculates the total cost, including discounts and balance verification.

## Requirements

To run this program, you need to have the following installed on your computer:

- Java Development Kit (JDK) version 8 or higher
- Java 21

### Setup

1. Clone the repository:

```sh
git clone https://github.com/your_username/check.git
cd check
```

2. Ensure your folder structure matches the following:

```css
src/
└── main/
    ├── java/
    │   └── ru/
    │       └── clevertec/
    │           └── check/
    │               └── CheckRunner.java
    └── resources/
        ├── discountCards.csv
        └── products.csv
```

### CSV File Format

##### discountCards.csv

The discount card data file should have the following structure:

```python
id;number;discount_amount
1;1111;3
2;2222;3
....
```

##### products.csv

The product data file should have the following structure:

```python
id;description;price;quantity_in_stock;wholesale_product
1;Milk;1.07;10;true
2;Cream 400g;2.71;20;true
3;Yogurt 400g;2.10;7;true
....
```

### Running the Program

Use the following command to run the program:

```sh
java -cp src ./src/main/java/ru/clevertec/check/CheckRunner.java id-quantity discountCard=xxxx balanceDebitCard=xxxx
```

Where:

- __`id`__ - product ID
- __`quantity`__ - quantity of product
- __`discountCard`__ - (optional) discount card number (4 digits)
- __`balanceDebitCard`__ - balance on the debit card

### Example

```sh
java -cp src ./src/main/java/ru/clevertec/check/CheckRunner.java 1-2 2-3 discountCard=1234 balanceDebitCard=50.00
```

In this example, the following are purchased:

- 2 units of the product with id 1
- 3 units of the product with id 2
- A discount card with number __`1234`__ is applied
- The balance on the debit card is __`50.00`__

### Output

The result of the program execution is written to the result.csv file in the following format:

```ruby
Date;Time
01.01.2024;12:00:00

QTY;DESCRIPTION;PRICE;DISCOUNT;TOTAL
2;Milk;1.00$;0.20$;1.80$
3;Bread;0.80$;0.12$;2.28$

DISCOUNT CARD;DISCOUNT PERCENTAGE
1234;10%

TOTAL PRICE;TOTAL DISCOUNT;TOTAL WITH DISCOUNT
4.08$;0.32$;3.76$
```

### Error Handling

The program outputs errors to the result.csv file in case of invalid arguments or insufficient funds on the debit card.

Example of an error:

```python
ERROR
BAD REQUEST
```

