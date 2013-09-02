package net.oesterholt.pcutmp3gui;


import java.io.File;
import java.util.Arrays;
import java.util.Vector;

import de.zebee.mpa.Cue;
import de.zebee.mpa.MainCLI;
import de.zebee.mpa.Track;
import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellEditEvent;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Callback;


public class PCutMP3Gui extends Application
{

	private TableView<Track> _table;
	private TextField        _performer;
	private TextField        _title;
	private Cue              _cue;
	 
	public void init() throws Exception {
		// create ui
		//root = FXMLLoader.load(DataApplication.class.getResource("dataapp.fxml"));
	}

	public void start(Stage stage) throws Exception {
		stage.setTitle("Perfect Cut MP3 - GUI");
		
		_table = new TableView<Track>();
		_cue = new Cue();
		//_cue.addTrack(1,  new Track("Hi", "Ulysses", "Album"));;
		//_cue.addTrack(2,  new Track("Hi hi", "Ulysses", "Album"));;
		
		TableColumn<Track, Integer> col_tracknumber = new TableColumn<Track, Integer>("Nr.");
		col_tracknumber.setMinWidth(50);
		PropertyValueFactory<Track, Integer> fac = new PropertyValueFactory<Track, Integer>("trackNumber");
		col_tracknumber.setCellValueFactory(fac);
		col_tracknumber.setCellFactory(new Callback<TableColumn<Track, Integer>, TableCell<Track, Integer>>() {
			public TableCell<Track, Integer> call(TableColumn<Track, Integer> c) {
				TableCell<Track, Integer> cell = new TableCell<Track, Integer>() {
					public void updateItem(Integer item, boolean empty) {
						super.updateItem(item, empty);
						setText(empty ? null : getString());
						setGraphic(null);
						setAlignment(Pos.CENTER_RIGHT);
					}
					
					private String getString() {
						return getItem() == null ? "" : getItem().toString();
					}
				};
				return cell;
			}
		});

		TableColumn col_title = new TableColumn<Track, String>("Title");
		col_title.setMinWidth(100);
	    col_title.setCellValueFactory(new PropertyValueFactory<Track, String>("title"));

	    TableColumn col_performer = new TableColumn<Track, String>("Performer");
		col_performer.setMinWidth(100);
	    col_performer.setCellValueFactory(new PropertyValueFactory<Track, String>("performer"));
	    
	    TableColumn col_length = new TableColumn<Track, String>("Length");
	    col_length.setMinWidth(100);
	    col_length.setCellValueFactory(new PropertyValueFactory<Track, String>("length"));;
		
		_table.setItems(_cue.getObservable());
		_table.getColumns().addAll(col_tracknumber, col_title, col_performer, col_length);
		
		_table.setEditable(true);
		//TextFieldTableCell<Track, String> tftc = new TextFieldTableCell<Track, String>();
		col_title.setCellFactory(TextFieldTableCell.forTableColumn());
		col_title.setOnEditCommit(
				new EventHandler<CellEditEvent<Track, String>>() {
					public void handle(CellEditEvent<Track, String> t) {
						((Track) t.getTableView().getItems().get(t.getTablePosition().getRow())).setTitle(t.getNewValue());
					}
				}
		);
		col_performer.setCellFactory(TextFieldTableCell.forTableColumn());
		col_title.setOnEditCommit(
				new EventHandler<CellEditEvent<Track, String>>() {
					public void handle(CellEditEvent<Track, String> t) {
						((Track) t.getTableView().getItems().get(t.getTablePosition().getRow())).setPerformer(t.getNewValue());
					}
				}
		);
		col_length.setCellFactory(TextFieldTableCell.forTableColumn());
		col_title.setOnEditCommit(
				new EventHandler<CellEditEvent<Track, String>>() {
					public void handle(CellEditEvent<Track, String> t) {
						((Track) t.getTableView().getItems().get(t.getTablePosition().getRow())).setLength(t.getNewValue());
					}
				}
		);
		
		
		Button btn = new Button("Add Below");
		btn.setOnAction(new EventHandler<ActionEvent>() {
			public void handle(ActionEvent event) {
				addBelow();
			}
		});
		
		Button btn1 = new Button("Remove");
		btn1.setOnAction(new EventHandler<ActionEvent>() {
			public void handle(ActionEvent event) {
				delSelected();
			}
		});
		
		Button btn2 = new Button("Insert Before");
		btn2.setOnAction(new EventHandler<ActionEvent>() {
			public void handle(ActionEvent event) {
				insertBefore();
			}
		});
		
		
		Label ltitle = new Label("Title: ");
		Label lperformer = new Label("Performer: ");
		_title = new TextField();
		_performer = new TextField();
		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(10);
		grid.add(ltitle, 0, 0);
		grid.add(lperformer,0,1);
		grid.add(_title,1,0);
		grid.add(_performer,1,1);
		
		HBox hbox = new HBox();
		hbox.setSpacing(5);
		hbox.getChildren().addAll(btn, btn1, btn2);

		MenuBar bar = new MenuBar();
		Menu file = new Menu("File");
		MenuItem open = new MenuItem("Open Cue");
		open.setOnAction(new EventHandler<ActionEvent>() {
			public void handle(ActionEvent e) {
				 FileChooser fileChooser = new FileChooser();
				 
	              //Set extension filter
	              FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("CUE Files (*.cue)", "*.cue");
	              fileChooser.getExtensionFilters().add(extFilter);
	             
	              //Show open file dialog
	              File file = fileChooser.showOpenDialog(null);

	              if (file != null) {
	            	  MainCLI cli = new MainCLI(new MainCLI.Report() {
						public void println(String msg) {
							System.out.println(msg);
						}
	            	  });
	            	  _cue.clear();
	            	  try {
	            		  cli.loadCUE(file.getAbsolutePath(), _cue);
	            		  _title.setText(_cue.getTitle());;
	            		  _performer.setText(_cue.getPerformer());
	            	  } catch (Exception exp) {
	            		  exp.printStackTrace();
	            	  }
	              }	              
			}
		});
		MenuItem save = new MenuItem("Save Cue");
		save.setOnAction(new EventHandler<ActionEvent>() {
			public void handle(ActionEvent e) {
				 FileChooser fileChooser = new FileChooser();
				 
	              //Set extension filter
	              FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("CUE Files (*.cue)", "*.cue");
	              fileChooser.getExtensionFilters().add(extFilter);
	             
	              //Show open file dialog
	              File file = fileChooser.showSaveDialog(null);
	              

			}
		});
		MenuItem quit = new MenuItem("Quit");
		quit.setOnAction(new EventHandler<ActionEvent>() {
			public void handle(ActionEvent e) {
				System.exit(0);
			}
		});
	    file.getItems().addAll(open, save, new SeparatorMenuItem(), quit);
	    bar.getMenus().addAll(file);

		VBox box = new VBox();
		box.setSpacing(5);;
		box.getChildren().addAll(bar, grid, hbox, _table);
		

		StackPane root = new StackPane();
		root.getChildren().addAll(box);
		stage.setScene(new Scene(root, 300, 250));
		stage.show();	
	}
	
	private void addBelow() {
		
	}
	
	private void insertBefore() {
		
	}
	
	private void delSelected() {
		
	}

	public static void main(String[] args) { 
		launch(args); 
	}
}
