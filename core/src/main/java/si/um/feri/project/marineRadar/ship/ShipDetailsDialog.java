package si.um.feri.project.marineRadar.ship;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;

public class ShipDetailsDialog extends Dialog {

    private Ship ship;
    private ShipSelectionListener listener;

    public interface ShipSelectionListener {
        void onTrack(Ship ship);
        void onFocusShip(Ship ship);
    }

    public ShipDetailsDialog(Ship ship, Skin skin, ShipSelectionListener listener) {
        super("", skin);
        this.ship = ship;
        this.listener = listener;

        build();
    }

    private void build() {
        getTitleLabel().setText("Vessel Information");

        Table content = getContentTable();
        content.pad(20);

        // Vessel identity section
        addSection(content, "VESSEL IDENTITY");
        addInfoRow(content, "Name:", ship.name);
        addInfoRow(content, "MMSI:", ship.mmsi);
        if (ship.callSign != null && !ship.callSign.isEmpty()) {
            addInfoRow(content, "Call Sign:", ship.callSign);
        }
        if (ship.imoNumber > 0) {
            addInfoRow(content, "IMO Number:", String.valueOf(ship.imoNumber));
        }
        addInfoRow(content, "Type:", ship.getShipTypeDescription());
        
        //debug locations
        addInfoRow(content, "Locations", String.valueOf(ship.locationHistory.size()));

        content.row();

        // Current status section
        addSection(content, "CURRENT STATUS");
        addInfoRow(content, "Position:", String.format("%.4f°, %.4f°", ship.lat, ship.lon));
        addInfoRow(content, "Speed:", String.format("%.1f knots", ship.speed));
        addInfoRow(content, "Course:", String.format("%.1f°", ship.course));
        addInfoRow(content, "Heading:", String.format("%.1f°", ship.heading));
        addInfoRow(content, "Status:", ship.getNavigationalStatusText());

        content.row();

        // Vessel characteristics
        if (ship.shipLength > 0 || ship.shipWidth > 0 || ship.draught > 0) {
            addSection(content, "VESSEL CHARACTERISTICS");
            if (ship.shipLength > 0 && ship.shipWidth > 0) {
                addInfoRow(content, "Dimensions:", ship.getFormattedSize());
            }
            if (ship.draught > 0) {
                addInfoRow(content, "Draught:", String.format("%.1f m", ship.draught));
            }
        }

        content.row();

        // Voyage information
        if (!ship.destination.equals("Unknown")) {
            addSection(content, "VOYAGE INFORMATION");
            addInfoRow(content, "Destination:", ship.destination);
            if (ship.eta != null && ship.eta.isValid()) {
                addInfoRow(content, "ETA:", ship.eta.toString());
            }
        }

        // Buttons
        Table buttonTable = getButtonTable();
        buttonTable.pad(10);

        TextButton trackButton = new TextButton("Track Vessel", getSkin());
        trackButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (listener != null) {
                    listener.onTrack(ship);
                }
                hide();
            }
        });

        TextButton focusButton = new TextButton("Focus & Zoom", getSkin());
        focusButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (listener != null) {
                    listener.onFocusShip(ship);
                }
                hide();
            }
        });

        TextButton closeButton = new TextButton("Close", getSkin());
        closeButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                hide();
            }
        });

        buttonTable.add(trackButton).pad(5).width(120);
        buttonTable.add(focusButton).pad(5).width(120);
        buttonTable.add(closeButton).pad(5).width(120);
    }

    private void addSection(Table table, String title) {
        Label sectionLabel = new Label(title, getSkin());
        sectionLabel.setColor(0.7f, 0.9f, 1f, 1f);
        table.add(sectionLabel).left().padTop(10).padBottom(5).colspan(2).row();
    }

    private void addInfoRow(Table table, String label, String value) {
        Label labelWidget = new Label(label, getSkin());
        labelWidget.setColor(0.8f, 0.8f, 0.8f, 1f);

        Label valueWidget = new Label(value, getSkin());

        table.add(labelWidget).left().padRight(10);
        table.add(valueWidget).left().expandX().row();
    }
}
