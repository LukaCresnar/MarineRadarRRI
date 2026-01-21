package si.um.feri.project.marineRadar.ship;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

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
    private TextButton filterButton;
    private boolean filterHighSpeed = false;

    private ShipSelectionListener selectionListener;



    public interface ShipSelectionListener {
        void onShipSelected(Ship ship);
        void onShipDetails(Ship ship);
        void onClose();
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
        Drawable bg;
        if (skin.has("default-rect", Drawable.class)) {
            bg = skin.getDrawable("default-rect");
        } else if (skin.has("rect", Drawable.class)) {
            bg = skin.getDrawable("rect");
        } else {
            Pixmap pm = new Pixmap(4, 4, Pixmap.Format.RGBA8888);
            pm.setColor(0.12f, 0.12f, 0.12f, 1f);
            pm.fill();
            Texture tex = new Texture(pm);
            pm.dispose();
            bg = new TextureRegionDrawable(new TextureRegion(tex));
        }
        setBackground(bg);
        pad(10);

        // Title and close button row
        Table titleRow = new Table();
        titleRow.add(new Label("Find Ship:", skin)).left().expandX();
        TextButton closeButton = new TextButton("X", skin);
        closeButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (selectionListener != null) {
                    selectionListener.onClose();
                }
            }
        });
        titleRow.add(closeButton).width(30).height(30).right();
        add(titleRow).colspan(2).fillX().row();

        searchField = new TextField("", skin);
        searchField.setMaxLength(50);
        searchField.setMessageText("Search ships...");
        add(searchField).width(200).padTop(10);
        
        // Filter button for ships above 5 knots
        filterButton = new TextButton("Moving: OFF", skin);
        filterButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                filterHighSpeed = !filterHighSpeed;
                updateFilterButtonAppearance();
                updateShipList();
            }
        });
        add(filterButton).width(100).padTop(10).padLeft(5).row();

        shipListContainer = new Table();
        shipListContainer.left().top();

        scrollPane = new ScrollPane(shipListContainer, skin);
        scrollPane.setScrollingDisabled(true, false);
        scrollPane.setHeight(300);

        add(new Label("Available Ships:", skin)).colspan(2).left().padTop(10).row();
        add(scrollPane).colspan(2).width(300).height(300).left().padTop(5).row();

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
        
        // Apply speed filter if enabled
        if (filterHighSpeed) {
            filteredShips = filteredShips.stream()
                .filter(ship -> ship.speed > 5.0)
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
        // Prefer a white background for list rows. Use a skin drawable named 'white' if present, otherwise create a white Pixmap fallback.
        Drawable rowBg;
        if (skin.has("white", Drawable.class)) {
            rowBg = skin.getDrawable("white");
        } else {
            Pixmap pm = new Pixmap(4, 4, Pixmap.Format.RGBA8888);
            pm.setColor(1f, 1f, 1f, 1f);
            pm.fill();
            Texture tex = new Texture(pm);
            pm.dispose();
            rowBg = new TextureRegionDrawable(new TextureRegion(tex));
        }
        row.setBackground(rowBg);
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
                // Single click: select the ship and notify listener (no double-click behavior)
                selectedShip = ship;
                highlightSelectedRow();

                if (selectionListener != null) {
                    selectionListener.onShipSelected(ship);
                    // Also request opening details (listener will handle showing dialog)
                    selectionListener.onShipDetails(ship);
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
    
    private void updateFilterButtonAppearance() {
        if (filterHighSpeed) {
            filterButton.setText("Moving: ON");
            filterButton.setColor(0.2f, 0.8f, 0.2f, 1f); // Green when active
        } else {
            filterButton.setText("Moving: OFF");
            filterButton.setColor(1f, 1f, 1f, 1f); // Normal color when inactive
        }
    }
}
