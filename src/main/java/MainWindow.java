import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.web.WebView;

public class MainWindow {
    @FXML
    public Button                  btn_checkForNewAds;
    @FXML
    public Label                   l_newAds;
    @FXML
    public ChoiceBox<Advertisment> cb_newAds;
    @FXML
    public Label                   l_viewedAds;
    @FXML
    public ChoiceBox<Advertisment> cb_viewedAds;
    @FXML
    public Label                   l_savedAds;
    @FXML
    public ChoiceBox<Advertisment> cb_savedAds;
    @FXML
    public Label                   l_filteredAds;
    @FXML
    public ChoiceBox<Advertisment> cb_filteredAds;
    @FXML
    public Button                  btn_delete;
    @FXML
    public Button                  btn_save;

    public ChoiceBox<Advertisment> aLastUsedChoiceBox;

    @FXML
    public WebView wv_mainWeb;

    private void setupChoiceBox(ChoiceBox<Advertisment> cb) {
        cb.getSelectionModel()
          .selectedItemProperty()
          .addListener((observable, oldValue, newValue) -> {
              aLastUsedChoiceBox = cb;
              wv_mainWeb.getEngine().load(newValue.getOriginalLink());
          });
    }

    @FXML
    public void initialize() {
        
    }

    public void onDelete(ActionEvent actionEvent) {

    }

    public void onSave(ActionEvent actionEvent) {

    }

    public void onCheckForNewAds(ActionEvent actionEvent) {

    }
}
