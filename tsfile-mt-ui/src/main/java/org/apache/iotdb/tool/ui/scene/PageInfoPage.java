package org.apache.iotdb.tool.ui.scene;

import javafx.stage.Modality;
import javafx.stage.StageStyle;
import org.apache.iotdb.tool.core.model.IPageInfo;
import org.apache.iotdb.tool.core.model.PageInfo;
import org.apache.iotdb.tool.ui.config.TableAlign;
import org.apache.iotdb.tool.ui.view.BaseTableView;
import org.apache.iotdb.tsfile.read.common.BatchData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Date;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

public class PageInfoPage {
  private static final Logger logger = LoggerFactory.getLogger(IoTDBParsePageV3.class);

  private static final double WIDTH = 810;
  private static final double HEIGHT = 300;

  private AnchorPane anchorPane;
  private Scene scene;
  private IoTDBParsePageV3 ioTDBParsePage;
  private Stage stage;

  private AnchorPane pageHeaderPane;

  private AnchorPane pageDataPane;

  private TreeItem<IoTDBParsePageV3.ChunkTreeItemValue> pageItem;
  // todo
  private ObservableList<IoTDBParsePageV3.TimesValues> tvDatas =
      FXCollections.observableArrayList();

  /** table datas */
  private TableView pageHeaderTableView;

  private TableView pageTVTableView;

  public PageInfoPage() {}

  public PageInfoPage(
      Stage stage,
      IoTDBParsePageV3 ioTDBParsePage,
      TreeItem<IoTDBParsePageV3.ChunkTreeItemValue> pageItem) {
    this.stage = stage;
    this.ioTDBParsePage = ioTDBParsePage;
    this.pageItem = pageItem;
    init(stage);
  }

  public Scene getScene() {
    return scene;
  }

  private void init(Stage stage) {
    // page of Aligned ChunkGroup
    if (!(pageItem.getValue().getParams() instanceof PageInfo)) {
      Stage chunkInfoStage = new Stage();
      chunkInfoStage.initStyle(StageStyle.UTILITY);
      chunkInfoStage.initModality(Modality.APPLICATION_MODAL);
      AlignedPageInfoPage alignedPageInfoPage = new AlignedPageInfoPage(stage, ioTDBParsePage, pageItem);
      return;
    }

    pageHeaderTableView = new TableView();
    pageTVTableView = new TableView();

    anchorPane = new AnchorPane();
    scene = new Scene(anchorPane, WIDTH, HEIGHT);
    stage.setScene(scene);
    stage.setTitle("Page Information");
    stage.show();
    stage.setResizable(false);

    // 数据来源
    ObservableList<IoTDBParsePageV3.PageInfo> pageDatas = FXCollections.observableArrayList();

    IPageInfo pageInfo = (PageInfo) pageItem.getValue().getParams();

    try {
      pageDatas.add(
          new IoTDBParsePageV3.PageInfo(
                  pageInfo.getUncompressedSize(),
                  pageInfo.getCompressedSize(),
                  pageInfo.getStatistics() == null ? "" : pageInfo.getStatistics().toString()));

      BatchData batchData =
          ioTDBParsePage.getTsFileAnalyserV13().fetchBatchDataByPageInfo((PageInfo) pageItem.getValue().getParams());
      while (batchData.hasCurrent()) {
        Object currValue = batchData.currentValue();
        this.tvDatas.add(
            new IoTDBParsePageV3.TimesValues(
                    new Date(batchData.currentTime()).toString(),
            currValue == null ? "" : currValue.toString()));
        batchData.next();
      }
    } catch (Exception e) {
      logger.error(
          "Failed to get page details, the page statistics:{}",
              pageInfo.getStatistics().toString());
    }

    BaseTableView baseTableView = new BaseTableView();
    // table 1 page statistic
    pageHeaderPane = new AnchorPane();
    pageHeaderPane.setLayoutX(0);
    pageHeaderPane.setLayoutY(0);
    pageHeaderPane.setPrefHeight(WIDTH);
    pageHeaderPane.setPrefWidth(HEIGHT * 0.1);
    anchorPane.getChildren().add(pageHeaderPane);
    TableColumn<String, String> uncompressedCol =
        baseTableView.genColumn(TableAlign.CENTER, "uncompressedSize", "uncompressedSize");
    TableColumn<String, String> compressedCol =
        baseTableView.genColumn(TableAlign.CENTER_LEFT, "compressedSize", "compressedSize");
    TableColumn<String, String> statisticsCol =
        baseTableView.genColumn(TableAlign.CENTER_LEFT, "statistics", "statistics");
    baseTableView.tableViewInit(
        pageHeaderPane,
        pageHeaderTableView,
        pageDatas,
        true,
        uncompressedCol,
        compressedCol,
        statisticsCol);
    pageHeaderTableView.setLayoutX(0);
    pageHeaderTableView.setLayoutY(0);
    pageHeaderTableView.setPrefWidth(WIDTH);
    pageHeaderTableView.setPrefHeight(HEIGHT);

    // table 2 page data
    pageDataPane = new AnchorPane();
    pageDataPane.setLayoutX(0);
    pageDataPane.setLayoutY(HEIGHT * 0.2);
    pageDataPane.setPrefHeight(WIDTH);
    pageHeaderPane.setPrefWidth(HEIGHT * 0.7);
    anchorPane.getChildren().add(pageDataPane);
    TableColumn<Date, String> timestampCol =
        baseTableView.genColumn(TableAlign.CENTER, "timestamp", "timestamp");
    TableColumn<String, String> valueCol =
        baseTableView.genColumn(TableAlign.CENTER_LEFT, "value", "value");
    baseTableView.tableViewInit(
        pageDataPane, pageTVTableView, tvDatas, true, timestampCol, valueCol);
    pageTVTableView.setLayoutX(0);
    pageTVTableView.setLayoutY(HEIGHT * 0.12);
    pageTVTableView.setPrefWidth(WIDTH);
    pageTVTableView.setPrefHeight(HEIGHT * 0.65);

    URL uiDarkCssResource = getClass().getClassLoader().getResource("css/ui-dark.css");
    if (uiDarkCssResource != null) {
      this.getScene().getStylesheets().add(uiDarkCssResource.toExternalForm());
    }
  }
}
