package Controller;

import java.sql.*;
import javax.swing.table.DefaultTableModel;
import Model.MenuItem;
import brothermanagement.PlaceOrder;
import database.brotherconnection;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import brothermanagement.BillFrame;

public class MenuItemController {
    private PlaceOrder view; // reference to the view
    
    public MenuItemController(PlaceOrder view){
        this.view = view;
    }
    
    // 🔍 Fetch product by ID from database
    public MenuItem getMenuItemById(int id) {
        MenuItem item = null;
        String sql = "SELECT pro_id, pro_name, pro_type, pro_price, pro_image, pro_stock, pro_status FROM product WHERE pro_id = ?";
        
        try (Connection conn = brotherconnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                int proId = rs.getInt("pro_id");
                String proName = rs.getString("pro_name");
                String proType = rs.getString("pro_type");
                int proPrice = rs.getInt("pro_price");
                String proImage = rs.getString("pro_image");
                int proStock = rs.getInt("pro_stock");
                boolean proStatus = rs.getBoolean("pro_status");
                
                item = new MenuItem(proId, proName, proType, proPrice, proImage, proStock, proStatus);
                System.out.println("Found the product: " + proName + " | Price: " + proPrice);
            } else {
                System.out.println("No product found with Id: " + id);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return item;
    }

    // ➕ Add or increment product in table model
    public void addOrIncrementItem(DefaultTableModel model, int itemId) {
    // Get the product by ID from the database
    MenuItem item = getMenuItemById(itemId);

    if (item == null) {
        System.out.println("Product not found!");
        return;
    }

    if (!item.isAvailable()) {
        System.out.println("Product not available!");
        return;
    }

    boolean found = false;

    // 🔁 Check if item already exists in the JTable
    for (int i = 0; i < model.getRowCount(); i++) {
        int currentId = Integer.parseInt(model.getValueAt(i, 0).toString());
        if (currentId == item.getId()) {
            int quantity = Integer.parseInt(model.getValueAt(i, 3).toString()) + 1;
            int subtotal = quantity * item.getPrice();
            model.setValueAt(quantity, i, 3);   // update quantity
            model.setValueAt(subtotal, i, 5);   // update subtotal
            found = true;
            break;
        }
    }

    // ➕ If not found, add as a new row
    if (!found) {
        int subtotal = item.getPrice();
        model.addRow(new Object[]{
            item.getId(),      // ID
            item.getName(),    // NAME
            item.getType(),    // CATEGORY (from pro_type)
            1,                 // QUANTITY
            item.getPrice(),   // PRICE
            subtotal           // SUBTOTAL
        });
    }
    updateTotal();
}


    // 🧮 Get quantity of an item from the table
    public int getItemQuantity(DefaultTableModel model, int itemId) {
        for (int i = 0; i < model.getRowCount(); i++) {
            int currentId = Integer.parseInt(model.getValueAt(i, 0).toString());
            if (currentId == itemId) {
                return Integer.parseInt(model.getValueAt(i, 3).toString());
            }
        }
        return 0;
    }

    // ❌ Delete selected product
    public void deleteSelectedItem(JTable table, PlaceOrder view) {
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        int selectedRow = table.getSelectedRow();

        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(null, "Please select an item to delete!");
            return;
        }

        int itemId = Integer.parseInt(model.getValueAt(selectedRow, 0).toString());
        model.removeRow(selectedRow);

        int quantity = 0;
        for (int i = 0; i < model.getRowCount(); i++) {
            int currentId = Integer.parseInt(model.getValueAt(i, 0).toString());
            if (currentId == itemId) {
                quantity = Integer.parseInt(model.getValueAt(i, 3).toString());
                break;
            }
        }

        view.updateItemLabel(itemId, quantity);
        updateTotal();
    }

    // 💰 Calculate total price
    public int calculateTotal(DefaultTableModel model) {
        int total = 0;
        for (int i = 0; i < model.getRowCount(); i++) {
            total += Integer.parseInt(model.getValueAt(i, 5).toString());
        }
        return total;
    }
    
    public void updateTotal() {
    JTable table = view.getOrderTable();
    JLabel totalLabel = view.getTotalLabel();

    DefaultTableModel model = (DefaultTableModel) table.getModel();
    int total = 0;

    for (int i = 0; i < model.getRowCount(); i++) {
        total += Integer.parseInt(model.getValueAt(i, 5).toString()); // column 5 = subtotal
    }

    totalLabel.setText("Total: " + total);

    }
    
    public void handleOrderAndPrint() {
        JTable table = view.getOrderTable(); 
        DefaultTableModel model = (DefaultTableModel) table.getModel();

        StringBuilder receipt = new StringBuilder();
        receipt.append("******** Brother Chicken ********\n");
        receipt.append("----------------------------------------\n");
        receipt.append(String.format("%-20s %-5s %s\n", "Item", "Qty", "Price"));
        receipt.append("----------------------------------------\n");

        double total = 0;

        for (int i = 0; i < model.getRowCount(); i++) {
            String item = model.getValueAt(i, 1).toString();   // Name column
            String qty = model.getValueAt(i, 3).toString();    // Quantity column
            String price = model.getValueAt(i, 5).toString();  // Subtotal column

            receipt.append(String.format("%-20s %-5s %s\n", item, qty, price));
            total += Double.parseDouble(price);
        }

        receipt.append("----------------------------------------\n");
        receipt.append(String.format("TOTAL: %,.0f VND\n", total));
        receipt.append("----------------------------------------\n");
        receipt.append("Thanks for choosing us!\n");

        // Show BillFrame window
        new BillFrame(receipt.toString(), view) {{
            setLocationRelativeTo(null);
            setDefaultCloseOperation(javax.swing.JFrame.DISPOSE_ON_CLOSE);
            setVisible(true);
        }};

    }
    
    public void confirmOrder() {
        JTable table = view.getOrderTable();
        DefaultTableModel model = (DefaultTableModel) table.getModel();

        double total = 0;

        // Calculate total
        for (int i = 0; i < model.getRowCount(); i++) {
            total += Double.parseDouble(model.getValueAt(i, 5).toString()); // subtotal column
        }


        // update database (bill + bill_items)
        saveBillToDatabase(model, total);
    }

    private void saveBillToDatabase(DefaultTableModel model, double total) {
        try (Connection conn = brotherconnection.getConnection()) {
            conn.setAutoCommit(false);

            // insert into bill table
            String sqlBill = "INSERT INTO bill (bill_date, total_amount) VALUES (NOW(), ?)";
            PreparedStatement psBill = conn.prepareStatement(sqlBill, Statement.RETURN_GENERATED_KEYS);
            psBill.setDouble(1, total);
            psBill.executeUpdate();

            ResultSet rs = psBill.getGeneratedKeys();
            int billId = -1;
            if (rs.next()) {
                billId = rs.getInt(1);
            }

            // insert into bill_items
            String sqlItem = "INSERT INTO bill_items (bill_id, product_id, product_name, qty, subtotal) VALUES (?, ?, ?, ?, ?)";
            PreparedStatement psItem = conn.prepareStatement(sqlItem);

            for (int i = 0; i < model.getRowCount(); i++) {
                int productId = Integer.parseInt(model.getValueAt(i, 0).toString());
                String productName = model.getValueAt(i, 1).toString();
                int qty = Integer.parseInt(model.getValueAt(i, 3).toString());
                double subtotal = Double.parseDouble(model.getValueAt(i, 5).toString());

                psItem.setInt(1, billId);
                psItem.setInt(2, productId);
                psItem.setString(3, productName);
                psItem.setInt(4, qty);
                psItem.setDouble(5, subtotal);
                psItem.addBatch();
            }

            psItem.executeBatch();
            conn.commit();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


}
