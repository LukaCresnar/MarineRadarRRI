package si.um.feri.project.marineRadar;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ShipSearchPanel extends Table {
    private final List<Ship> allShips;
    private final Skin skin;
    private List<Ship> filteredShips;
    private Ship selectedShip = null;
    
    private TextField searchField;
    private List<Table> shipRows;
    private Table shipListContainer;
    private ScrollPane scrollPane;
    
    private ShipSelectionListener selectionListener;
    
    private long lastClickTime = 0;
    private Ship lastClickedShip = null;
    private static final long DOUBLE_CLICK_TIME = 300; 
    
    public interface ShipSelectionListener {
        void onShipSelected(Ship ship);
        void onShipDoubleClicked(Ship ship);
    }
    
    public ShipSearchPanel(List<Ship> ships, Skin skin, ShipSelectionListener listener) {
        super(skin);
        this.allShips = ships;
        this.skin = skin;
        this.selectionListener = listener;
        this.filteredShips = new ArrayList<>(ships);
        this.shipRows = new ArrayList<>();
        
        setupUI();
    }
    
    private void setupUI() {
        setBackground("default-rect");
        pad(10);
        
        searchField = new TextField("", skin);
        searchField.setMaxLength(50);
        searchField.setMessageText("Search ships...");
        add(new Label("Find Ship:", skin)).padRight(5);
        add(searchField).width(200).padRight(5).row();
        
        shipListContainer = new Table();
        shipListContainer.left().top();
        
        scrollPane = new ScrollPane(shipListContainer, skin);
        scrollPane.setScrollingDisabled(true, false);
        scrollPane.setHeight(300);
        
        add(new Label("Available Ships:", skin)).left().padTop(10).row();
        add(scrollPane).width(300).height(300).left().padTop(5).row();
        
        searchField.addListener(event -> {
            if (event instanceof ChangeListener.ChangeEvent) {
                updateShipList();
            }
            return false;
        });
        
        updateShipList();
    }
    
    public void updateShipList() {
        String searchText = searchField.getText().toLowerCase().trim();
        
        if (searchText.isEmpty()) {
            filteredShips = new ArrayList<>(allShips);
        } else {
            filteredShips = allShips.stream()
                .filter(ship -> ship.name.toLowerCase().contains(searchText) || 
                               ship.mmsi.contains(searchText))
                .collect(Collectors.toList());
        }
        
        shipListContainer.clearChildren();
        shipRows.clear();
        
        for (Ship ship : filteredShips) {
            Table shipRow = createShipRow(ship);
            if (ship == selectedShip) {
                shipRow.setColor(0.2f, 0.4f, 0.8f, 1f); 
            }
            shipListContainer.add(shipRow).width(280).left().padBottom(5).row();
        }
    }
    
    private Table createShipRow(Ship ship) {
        Table row = new Table(skin);
        row.setBackground("default-rect");
        row.pad(8);
        
        String shipInfo = String.format("%s (%s)", ship.name, ship.mmsi);
        String posInfo = String.format("%.4f, %.4f | Speed: %.1f kn", ship.lat, ship.lon, ship.speed);
        
        Label nameLabel = new Label(shipInfo, skin);
        Label posLabel = new Label(posInfo, skin);
        
        row.add(nameLabel).left().row();
        row.add(posLabel).left();
        
        row.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                long currentTime = System.currentTimeMillis();
                
                if (lastClickedShip == ship && (currentTime - lastClickTime) < DOUBLE_CLICK_TIME) {
                    if (selectionListener != null) {
                        selectionListener.onShipDoubleClicked(ship);
                    }
                    lastClickedShip = null;
                    lastClickTime = 0;
                } else {
                    selectedShip = ship;
                    if (selectionListener != null) {
                        selectionListener.onShipSelected(ship);
                    }
                    highlightSelectedRow();
                    lastClickedShip = ship;
                    lastClickTime = currentTime;
                }
            }
        });
        
        shipRows.add(row);
        return row;
    }
    
    private void highlightSelectedRow() {
        for (int i = 0; i < shipRows.size(); i++) {
            Table row = shipRows.get(i);
            if (filteredShips.get(i) == selectedShip) {
                row.setColor(0.2f, 0.4f, 0.8f, 1f);
            } else {
                row.setColor(1f, 1f, 1f, 1f); 
            }
        }
    }
    
    public Ship getSelectedShip() {
        return selectedShip;
    }
    
    public void setSelectedShip(Ship ship) {
        this.selectedShip = ship;
        highlightSelectedRow();
    }
    
    public void refreshShips() {
        updateShipList();
    }
}
