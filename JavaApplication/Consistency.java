package JavaApplication;
/*THE PROGRAM IS A FOLLOW UP TO ALLOW FOR THE PROGRAM TO DEMONSTRATE THE CONSISTENCY OF THE BANK ACCOUNT BALANCE AFTER MULTIPLE THREADS HAVE PERFORMED DEPOSITS, WITHDRAWALS AND TRANSFERS
*/



import java.util.concurrent.locks.ReentrantLock;
class BankAccount {
    private final ReentrantLock lock = new ReentrantLock();
    private final String accountName;
    private final long initialBalance;   // NEW: remembers the starting balance
    private long balance;
    private long totalDeposited = 0;     // NEW: sum of all SUCCESSFUL deposits
    private long totalWithdrawn = 0;     // NEW: sum of all SUCCESSFUL withdrawals

    public BankAccount(String accountName, long balance) {
        this.accountName = accountName;
        this.balance = balance;
        this.initialBalance = balance;
    }

    private void pause() { // Simulate a real-world delay in banking transactions
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public String getAccountName() {
        return accountName;
    }

    public long getBalance() {
        lock.lock();
        try {
            return balance;
        } finally {
            lock.unlock();
        }
    }

    public void deposit(long amount) {
        deposit(amount, true); // the lock is already taken inside deposit(amount, print)
    }

    public void deposit(long amount, boolean print) {
        lock.lock();
        try {
            if (amount > 0) {
                long amount_0 = balance;
                pause();
                balance = amount_0 + amount;
                totalDeposited += amount;   // NEW: only counted when the deposit succeeds
                if (print) {
                    System.out.println("DEPOSITED " + amount + " INTO " + accountName + " | NEW BALANCE :" + balance + " \n");
                }
            } else if (print) {
                System.out.println("Deposit amount must be positive. \n");
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean withdraw(long amount) {
        return withdraw(amount, true);
    }

    public boolean withdraw(long amount, boolean print) {
        lock.lock();
        try {
            if (amount > 0 && amount <= balance) {
                long amount_0 = balance;
                pause();
                balance = amount_0 - amount;
                totalWithdrawn += amount;   // NEW: only counted when the withdrawal succeeds
                if (print) {
                    System.out.println("WITHDREW " + amount + " FROM " + accountName + " | NEW BALANCE : " + balance + " \n");
                }
                return true;
            } else {
                if (print) {
                    System.out.println("UNSUCCESSFUL WITHDRAWAL OF " + amount + " FROM " + accountName + " | PLEASE CHECK YOUR BALANCE!! \n");
                }
                return false;
            }
        } finally {
            lock.unlock();
        }
    }

    public void transfer(BankAccount target, long amount) {
        lock.lock();
        try {
            // withdraw/deposit update the counters of each account automatically
            if (this.withdraw(amount, false)) {
                target.deposit(amount, false);
                System.out.println("TRANSFERRED AN AMOUNT OF " + amount + " FROM THE ACCOUNT " + this.accountName + " TO THE ACCOUNT " + target.accountName + " | NEW BALANCE : " + this.balance + " \n");
            } else {
                System.out.println("FAILED TO TRANSFER " + amount + " FROM THE ACCOUNT " + this.accountName + " TO THE ACCOUNT " + target.accountName + " PLEASE CHECK ACCOUNT BALANCE!! \n");
            }
        } finally {
            lock.unlock();
        }
    }

    // NEW: initial balance + successful deposits - successful withdrawals must equal the final balance
    public boolean verifyBalance() {
        lock.lock();
        try {
            long expected = initialBalance + totalDeposited - totalWithdrawn;
            boolean ok = (expected == balance);
            System.out.println("---- Verification for " + accountName + " ----");
            System.out.println("Initial balance        : " + initialBalance);
            System.out.println("+ Successful deposits  : " + totalDeposited);
            System.out.println("- Successful withdrawals: " + totalWithdrawn);
            System.out.println("= Expected balance     : " + expected);
            System.out.println("  Actual balance       : " + balance);
            System.out.println(ok ? "  RESULT: CONSISTENT\n" : "  RESULT: INCONSISTENT!!\n");
            return ok;
        } finally {
            lock.unlock();
        }
    }
}

// Thread 1: Customer A Withdrawing
class A_Withdraw extends Thread {
    private final BankAccount a;
    private final long amount = 20000;

    A_Withdraw(BankAccount a) {
        this.a = a;
    }

    @Override
    public void run() {
        a.withdraw(amount, true);
    }
}

// Thread 2: Customer B depositing
class B_Deposit extends Thread {
    private final BankAccount b;
    private final long amount = 15000;

    B_Deposit(BankAccount b) {
        this.b = b;
    }

    @Override
    public void run() {
        b.deposit(amount, true);
    }
}

// Thread 3: Customer C Transferring
class C_Transfer extends Thread {
    private final BankAccount c;
    private final BankAccount c1;
    private final long amount = 30000;

    C_Transfer(BankAccount c, BankAccount c1) {
        this.c = c;
        this.c1 = c1;
    }

    @Override
    public void run() {
        c.transfer(c1, amount);
    }
}

// Thread 4: Customer D withdrawing
class D_Withdraw extends Thread {
    private final BankAccount d;
    private final long amount = 25000;

    D_Withdraw(BankAccount d) {
        this.d = d;
    }

    @Override
    public void run() {
        d.withdraw(amount, true);
    }
}

// Thread 5: Customer E making an online purchase seen as a Withdrawal
class E_Withdraw extends Thread {
    private final BankAccount e;
    private final long amount = 10000;

    E_Withdraw(BankAccount e) {
        this.e = e;
    }

    @Override
    public void run() {
        e.withdraw(amount, true);
    }
}

// Thread 6: Customer F Checking Balance Inquiry
class F_Inquiry extends Thread {
    private final BankAccount f;

    F_Inquiry(BankAccount f) {
        this.f = f;
    }

    @Override
    public void run() {
        System.out.println("Bank Balance : " + f.getBalance() + " \n");
    }
}

public class Consistency{
    public static void main(String[] args) {
        BankAccount shared = new BankAccount("B00", 100000);
        BankAccount other = new BankAccount("C00", 250000);

        Thread[] thread = {
                new A_Withdraw(shared),
                new B_Deposit(shared),
                new C_Transfer(shared, other),
                new D_Withdraw(shared),
                new E_Withdraw(shared),
                new F_Inquiry(shared)
        };

        for (Thread t : thread) {
            t.start();
        }

        // Wait for ALL threads to finish before reading the final result
        for (Thread t : thread) {
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.out.println("Final Balance of B00: " + shared.getBalance());
        System.out.println("Final Balance of C00: " + other.getBalance() + "\n");

        boolean ok1 = shared.verifyBalance();
        boolean ok2 = other.verifyBalance();

        System.out.println(ok1 && ok2
                ? "ALL ACCOUNTS CONSISTENT"
                : "BALANCE MISMATCH DETECTED");
    }
}