import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;



public class ThriftStoreSimulator {


    // Console Output Colours
    String RESET = "\u001B[0m";
    String RED = "\u001B[31m";
    String GREEN = "\u001B[32m";
    String YELLOW = "\u001B[33m";
    String BLUE = "\u001B[34m";
    String PURPLE = "\u001B[35m";

    // Constants
    public static int TICK_TIME_SIZE; // in milliseconds
    public static double TICKS_PER_DAY;
    public static final double DEPOSIT_PROBABILITY = 0.01; // 1% chance of deposit per tick
    public static final double CUSTOMER_PROBABILITY = 0.10; // 10% chance of customer per tick
    public static final int WALK_TIME = 10; // in ticks

    public static int CUSTOMER_SERVED = 0;

    // Data structures
    private final BlockingQueue<Map<String, Integer>> deliveryBox = new LinkedBlockingQueue<>();
    private final Map<String, Integer> thriftStoreSections = new HashMap<>();
    private final Random random = new Random();
    private final Lock deliveryLock = new ReentrantLock();
    private final Map<String, Lock> sectionLocks = new HashMap<>();
    private final Map<String, Integer> customersWaiting = new HashMap<>();
    private final Map<String, Integer> deliveredItems = new HashMap<>();
    private final List<Thread> threads = new ArrayList<Thread>();

    // Other variables
    private int tickCount = 0;
    private int numAssistants;
    private int totalWaitTime = 0;
    private int totalWorkTime = 0;
    private int totalBreakTime = 0;

        /**
         * Creates and shows the GUI for the Thrift Store Simulator.
         * This method creates a JFrame and adds components such as JLabels, JTextFields, and a JButton to it.
         * The user can enter values in the JTextFields and click the "Start Simulation" button to start the simulation.
         * The values entered by the user are used to set the parameters for the simulation.
         * After starting the simulation, the JFrame is disposed.
         */
        public void createAndShowGUI() {
            // Create a new JFrame
            JFrame frame = new JFrame("Thrift Store Simulator");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            // Create a JPanel to hold the components
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

            // Create JLabels for each input prompt
            JLabel assistantLabel = new JLabel("Enter number of assistants: ");
            JLabel ticksLabel = new JLabel("Enter the number of ticks in the run: ");
            JLabel tickTimeLabel = new JLabel("Enter tick time size (in ms): ");

            // Create JTextFields for user input
            JTextField assistantField = new JTextField(10);
            JTextField ticksField = new JTextField(10);
            JTextField tickTimeField = new JTextField(10);

            // Add components to the panel
            panel.add(assistantLabel);
            panel.add(assistantField);
            panel.add(ticksLabel);
            panel.add(ticksField);
            panel.add(tickTimeLabel);
            panel.add(tickTimeField);

            JButton startButton = new JButton("Start Simulation");

            startButton.addActionListener(e -> {
                numAssistants = Integer.parseInt(assistantField.getText());
                TICKS_PER_DAY = Double.parseDouble(ticksField.getText());
                TICK_TIME_SIZE = Integer.parseInt(tickTimeField.getText());
                start();
                frame.dispose();
            });
            panel.add(startButton);
            frame.getContentPane().add(panel);
            frame.pack();
            frame.setVisible(true);
        }


    /**
     * Initializes the thrift store sections with default values.
     * Each section is initialized with a capacity of 5 items.
     * Also initializes the section locks and customers waiting count for each section.
     */
    private void initializeThriftStoreSections() {
        thriftStoreSections.put("electronics",5);
        thriftStoreSections.put("clothing", 5);
        thriftStoreSections.put("furniture", 5);
        thriftStoreSections.put("toys", 5);
        thriftStoreSections.put("sporting goods", 5);
        thriftStoreSections.put("books", 5);

        // Initialize section locks
        for (String section : thriftStoreSections.keySet()) {
            sectionLocks.put(section, new ReentrantLock());
            customersWaiting.put(section, 0);
        }
    }

    // Main method
    public static void main(String[] args) {
        ThriftStoreSimulator simulator = new ThriftStoreSimulator();
        SwingUtilities.invokeLater(simulator::createAndShowGUI);
        // return;
    }

    // Start the simulation
    /**
     * Starts the simulation of the thrift store.
     * Initializes thrift store sections, creates assistant threads, and simulates customer behavior.
     * Prints tick information, handles deliveries, and moves the tick along.
     * Prints end-of-day statistics and exits the program.
     */
    public void start() {
        initializeThriftStoreSections();
        for (int i = 1; i <= numAssistants; i++) {
            Assistant assistant = new Assistant(i, 0, 0);
            threads.add(assistant);
            assistant.start();
        }
        int customerCount = 0;
            while(tickCount < TICKS_PER_DAY){
            if (tickCount % 100 == 0) {
                System.out.print(GREEN + "<" + tickCount + "> " + "Stock: ");
                for (Map.Entry<String, Integer> entry : thriftStoreSections.entrySet()) {
                    String section = entry.getKey();
                    int itemCount = entry.getValue();
                    System.out.print(GREEN + section + "=" + itemCount + " " + RESET);
                }
                System.out.println("");
            }
            tickCount++;
            if (random.nextDouble() < CUSTOMER_PROBABILITY ) {  // Create customer with a 10% probability every tick
                customerCount++;
                Customer customer = new Customer(customerCount);
                customer.start();
            }
            if (random.nextDouble() < DEPOSIT_PROBABILITY) {
                Map<String, Integer> deliveryItems = generateDelivery();
                deliveryLock.lock();
                try {
                    printDelivery(tickCount, Thread.currentThread().threadId(), deliveryItems);
                    deliveryBox.add(deliveryItems);
                } finally {
                    deliveryLock.unlock();
                }
            }
            // else {
            try {
                Thread.sleep(TICK_TIME_SIZE); // move the tick along by one
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // }
        }
        System.out.println("Tick = " + tickCount + ", End of day");
        printEndOfDayStats();
        System.exit(0);
        return;
    }


    // Generate delivery items
    /**
     * Generates a delivery of items for the thrift store.
     *
     * @return a map containing the sections of the thrift store as keys and the number of items to be delivered as values
     */
    public Map<String, Integer> generateDelivery() {
        Map<String, Integer> deliveryItems = new HashMap<>();
        List<String> sectionsList = new ArrayList<>(thriftStoreSections.keySet());
        Collections.sort(sectionsList, (section1, section2) -> customersWaiting.get(section2) - customersWaiting.get(section1));
        for(int remainingItems=10; remainingItems>0; remainingItems--){
            for (String section : sectionsList) {
                if ((thriftStoreSections.get(section) + deliveredItems.getOrDefault(section, 0)) < 10) {
                    int maxItemCount = Math.min(remainingItems, 10 - thriftStoreSections.get(section) - deliveredItems.getOrDefault(section, 0));
                    int itemCount = random.nextInt(maxItemCount + 1);
                    deliveryItems.put(section, itemCount);
                    remainingItems -= itemCount;
                } else {
                    deliveryItems.put(section, 0);
                }
            }
        }
        return deliveryItems;
    }

    // Print delivery information
    public void printDelivery(int tickCount, long threadId, Map<String, Integer> deliveryItems) {
        System.out.print(RED + "<" + tickCount + "> " + Thread.currentThread().threadId() + " Deposit_of_items : " + RESET);
        for (Map.Entry<String, Integer> entry : deliveryItems.entrySet()) {
            String section = entry.getKey();
            int itemCount = entry.getValue();
            deliveredItems.put(section, itemCount);
            System.out.print(RED + section + "=" + itemCount + " " + RESET);
        }
        System.out.println("");
    }

    /**
     * Prints the end of day statistics for the thrift store.
     * This method displays the remaining items in each section,
     * the total number of customers served, the average wait time
     * for customers served, the average physically working time for
     * assistants, and the average wasted time for assistants.
     */
    public void printEndOfDayStats() {
        System.out.println(PURPLE + "End of Day Statistics:" + RESET);
        System.out.println(PURPLE + "------------------------" + RESET);


        // Remaining items in each section
        System.out.println(PURPLE + "Remaining items in each section:" + RESET);
        for (Map.Entry<String, Integer> entry : thriftStoreSections.entrySet()) {
            String section = entry.getKey();
            int remainingItems = entry.getValue();
            System.out.println(PURPLE + section + ": " + remainingItems + RESET);
        }

        // Remaining items in each section
        System.out.println(PURPLE + "Total number of customers served: " + CUSTOMER_SERVED  + RESET);


        // Average wait time for customers served
        double averageWaitTime = CUSTOMER_SERVED == 0 ? 0 : (double) totalWaitTime / CUSTOMER_SERVED;
        DecimalFormat df = new DecimalFormat("#.##");
        System.out.println(PURPLE + "Average wait time for customers served: " + df.format(averageWaitTime) + " ticks" + RESET);

        // Average working & wasted time for assistants
        System.out.println(PURPLE + "Average physically working (moving & stocking) time for assistants: " + df.format(totalWorkTime / numAssistants) + " ticks " + RESET);
        System.out.println(PURPLE + "Average wasted time for assistants (waiting for an action and not on break): " + df.format(((TICKS_PER_DAY * numAssistants) - totalWorkTime - totalBreakTime) / numAssistants) + " ticks " + RESET);
        System.out.println(PURPLE + "------------------------" + RESET);
    }

    // Assistant class
    class Assistant extends Thread {
        private int assistantId;
        private int lastAction;
        private int lastBreak;
        private Map<String, Integer> itemsBeingStocked;


        public Assistant(int assistantId,int lastAction, int lastBreak){
            this.assistantId = assistantId;
            this.lastAction = lastAction;
            this.lastBreak = lastBreak;
            itemsBeingStocked = new HashMap<>();
        }

        /**
         * Executes the logic for the assistant's work during the simulation.
         * This method is called when the assistant thread starts.
         * It continuously runs until the tick count reaches the maximum number of ticks per day.
         * During each tick, the assistant takes items from the delivery box and restocks them in the store.
         * If the tick count exceeds a certain threshold since the last break, the assistant takes a break.
         * The break duration is 150 times the tick time size.
         * The total break time is accumulated during the simulation.
         */
        @Override
        public synchronized void run() {
            int waitTime = tickCount;
            while (tickCount < TICKS_PER_DAY) {
                try {
                    Map<String, Integer> items = deliveryBox.take();
                    restockItems(items, waitTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (tickCount - this.lastBreak > 200) {
                    try {
                        System.out.println(BLUE + "<" + tickCount + "> " + Thread.currentThread().threadId() + " Assistant=" + assistantId + " took a break" + RESET);
                        Thread.sleep(150 * TICK_TIME_SIZE);
                        totalBreakTime = totalBreakTime + 150;
                        System.out.println(BLUE + "<" + tickCount + "> " + Thread.currentThread().threadId() + " Assistant=" + assistantId + " is back from their break" + RESET);
                        this.lastBreak = tickCount;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        /**
         * Restocks the items in the thrift store.
         *
         * @param items     a map containing the items to be restocked, where the key is the section name and the value is the number of items
         * @param waitTime  the wait time in ticks before restocking the items
         */
        private synchronized void restockItems(Map<String, Integer> items, int waitTime) {
            System.out.print("<" + tickCount + "> " + Thread.currentThread().threadId() + " Assistant=" + assistantId + " collected_items " + PURPLE + "waited_ticks=" + (tickCount - this.lastAction) + RESET + ": ");
            for (Map.Entry<String, Integer> entry : items.entrySet()) {
                String section = entry.getKey();
                int itemCount = entry.getValue();
                itemsBeingStocked.put(section, thriftStoreSections.get(section) + itemCount);
                if (itemCount > 0){
                    System.out.print(section + "=" + itemCount + " ");
                }
            }
            System.out.println("");

            for (Map.Entry<String, Integer> entry : items.entrySet()) {
                String section = entry.getKey();
                if (entry.getValue() > 1 && entry.getValue() <= 10) {
                    Lock sectionLock = sectionLocks.get(section);
                    sectionLock.lock();
                    moveNextSection(section);
                    beginStockingSection(section, entry.getValue());
                    finishStockingSection(section);
                    sectionLock.unlock();
                }
            }
        }

        /**
         * Moves the assistant to the next section.
         *
         * @param section the section to move to
         */
        private synchronized void moveNextSection(String section) {
            try {
                Thread.sleep(WALK_TIME * TICK_TIME_SIZE);
                totalWorkTime = totalWorkTime + WALK_TIME;
                System.out.println("<" + tickCount + "> " + Thread.currentThread().threadId() + " Assistant=" + assistantId + " moved_to_section : " + section);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        /**
         * Begins stocking a section with a specified amount of items.
         *
         * @param section The section to be stocked.
         * @param amountOfItems The number of items to be stocked.
         */
        private synchronized void beginStockingSection(String section, int amountOfItems) {
            System.out.println("<" + tickCount + "> " + Thread.currentThread().threadId() + " Assistant=" + assistantId + " began_stocking_section : " + section + "=" + itemsBeingStocked.get(section));
            try {
                totalWorkTime = totalWorkTime + amountOfItems;
                Thread.sleep(TICK_TIME_SIZE * amountOfItems); // wait for items to be stocked
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        /**
         * Finishes stocking the specified section in the thrift store.
         *
         * @param section The section to finish stocking.
         */
        private synchronized void finishStockingSection(String section) {
            System.out.println(YELLOW + "<" + tickCount + "> " + Thread.currentThread().threadId() + " Assistant=" + assistantId + " finished_stocking_section : " + section + "=" + itemsBeingStocked.get(section) + RESET);
            thriftStoreSections.put(section, itemsBeingStocked.get(section));
            itemsBeingStocked.remove(section);
            lastAction = tickCount;
        }
}

    // Customer class
    class Customer extends Thread {
        private int customerId;

        public Customer(int customerId) {
            this.customerId = customerId;
        }

    /**
     * Executes the logic for a customer thread.
     *
     * This method is responsible for simulating a customer visiting the thrift store and buying an item from a random section.
     * It acquires a lock for the random section, checks if there are remaining items, and if so, the customer buys the item.
     * The method also updates the customer count, wait time, and notifies the main thread about the customer being served.
     *
     * @throws InterruptedException if the thread is interrupted while sleeping
     */
    @Override
    public synchronized void run() {
        Integer waitTime = tickCount;
        List<String> sections = new ArrayList<>(thriftStoreSections.keySet());
        String randomSection = sections.get(random.nextInt(sections.size()));
        int currentlyWaiting = customersWaiting.get(randomSection) + 1;
        customersWaiting.put(randomSection, currentlyWaiting);
        while (tickCount < TICKS_PER_DAY) {
            Lock sectionLock = sectionLocks.get(randomSection);
            sectionLock.lock();
            try {
                int remainingItems = thriftStoreSections.get(randomSection);
                if (remainingItems > 0) {
                    thriftStoreSections.put(randomSection, remainingItems - 1);
                    System.out.println("<" + tickCount + ">" + Thread.currentThread().threadId() + " Customer=" + customerId + " bought_from_section=" + randomSection + PURPLE + " waited_ticks=" + (tickCount - waitTime) + RESET);
                    Thread.sleep(TICK_TIME_SIZE * 1);
                    CUSTOMER_SERVED++;
                    totalWaitTime += tickCount - waitTime;
                    customersWaiting.put(randomSection, customersWaiting.get(randomSection) - 1);
                    this.interrupt();
                    return;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                sectionLock.unlock();
            }
        }
    }
}

}
