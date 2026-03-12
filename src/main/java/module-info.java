module org.example.examenjava {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;

    requires com.dlsc.formsfx;
    requires org.kordamp.bootstrapfx.core;

    requires jakarta.persistence;
    requires org.hibernate.orm.core;
    requires java.sql;
    requires java.logging;
    requires java.desktop;
    requires java.prefs;

    opens org.example.examenjava to javafx.fxml;
    opens org.example.examenjava.Entity to org.hibernate.orm.core, javafx.base;
    opens org.example.examenjava.network to javafx.fxml;

    exports org.example.examenjava;
    exports org.example.examenjava.Entity;
    exports org.example.examenjava.Repository;
    exports org.example.examenjava.network;
    exports org.example.examenjava.server;
}
