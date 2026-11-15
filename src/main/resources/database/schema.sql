CREATE TABLE IF NOT EXISTS USERS (
    user_id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT NOT NULL UNIQUE CHECK (length(trim(username)) > 0),
    password TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS TRANSACTIONS (
    transaction_id INTEGER PRIMARY KEY AUTOINCREMENT,
    date TEXT NOT NULL,
    description TEXT NOT NULL CHECK (length(trim(description)) > 0),
    type TEXT NOT NULL CHECK (type IN ('Income', 'Expense')),
    amount INTEGER NOT NULL CHECK (typeof(amount) = 'integer' AND amount > 0),
    user_id INTEGER NOT NULL,
    FOREIGN KEY (user_id) REFERENCES USERS(user_id)
);
