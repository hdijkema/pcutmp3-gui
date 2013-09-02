package net.oesterholt.pcutmp3gui;


import java.awt.TrayIcon;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Vector;

import de.zebee.mpa.Cue;
import de.zebee.mpa.MainCLI;
import de.zebee.mpa.Track;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellEditEvent;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Callback;


public class PCutMP3Gui extends Application
{

	private TableView<Track> _table;
	private TextField        _performer;
	private TextField        _title;
	private Button			 _mp3File;
	private String			 _mp3Path;
	private Cue              _cue;
	private File			 _cueFile; 
	private TextArea  		 _result;
	
	public void init() throws Exception {
		// create ui
		//root = FXMLLoader.load(DataApplication.class.getResource("dataapp.fxml"));
	}

	public void start(final Stage stage) throws Exception {
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
		col_title.setMinWidth(250);
	    col_title.setCellValueFactory(new PropertyValueFactory<Track, String>("title"));

	    TableColumn col_performer = new TableColumn<Track, String>("Performer");
		col_performer.setMinWidth(150);
	    col_performer.setCellValueFactory(new PropertyValueFactory<Track, String>("performer"));
	    
	    TableColumn col_point = new TableColumn<Track, String>("Split Point");
	    col_point.setMinWidth(75);
	    col_point.setCellValueFactory(new PropertyValueFactory<Track, String>("point"));;
		
		_table.setItems(_cue.getObservable());
		_table.getColumns().addAll(col_tracknumber, col_title, col_performer, col_point);
		
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
		col_performer.setOnEditCommit(
				new EventHandler<CellEditEvent<Track, String>>() {
					public void handle(CellEditEvent<Track, String> t) {
						((Track) t.getTableView().getItems().get(t.getTablePosition().getRow())).setPerformer(t.getNewValue());
					}
				}
		);
		col_point.setCellFactory(TextFieldTableCell.forTableColumn());
		col_point.setOnEditCommit(
				new EventHandler<CellEditEvent<Track, String>>() {
					public void handle(CellEditEvent<Track, String> t) {
						((Track) t.getTableView().getItems().get(t.getTablePosition().getRow())).setPoint(t.getNewValue());
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
		
		Button split = new Button("Save & Split!");
		split.setOnAction(new EventHandler<ActionEvent>() {
			public void handle(ActionEvent event) {
				split();
			}
		});
		
		_mp3File = new Button("...");
		_mp3File.setOnAction(new EventHandler<ActionEvent>() {
			public void handle(ActionEvent event) {
				Prefs p = new Prefs();
				FileChooser fileChooser = new FileChooser();
				 
	              //Set extension filter
	              FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("MP3 Files (*.mp3)", "*.mp3");
	              fileChooser.getExtensionFilters().add(extFilter);
	              if (p.getCueLoc() != null) {
	            	  fileChooser.setInitialDirectory(new File(p.getCueLoc()));
	              }
	             
	              //Show open file dialog
	              File file = fileChooser.showOpenDialog(null);
	
	              if (file != null) {	
	            	  _mp3File.setText(file.getName());
	            	  _mp3Path = file.getAbsolutePath();
	
	              }
			}
		});
		
		
		Label ltitle = new Label("Title: ");
		Label lperformer = new Label("Performer: ");
		_title = new TextField();
		_performer = new TextField();
		_title.setMinWidth(400);
		_performer.setMinWidth(400);
		GridPane grid = new GridPane();
		grid.setPadding(new Insets(10, 10, 10, 10));
		grid.setHgap(10);
		grid.setVgap(10);
		grid.add(ltitle, 0, 0);
		grid.add(lperformer,0,1);
		grid.add(_title,1,0);
		grid.add(_performer,1,1);
		grid.add(_mp3File, 1, 2);
		
		HBox hbox = new HBox();
		hbox.setSpacing(5);
		hbox.getChildren().addAll(btn, btn1, btn2, split);

		MenuBar bar = new MenuBar();
		Menu file = new Menu("File");
		MenuItem open = new MenuItem("Open Cue");
		open.setOnAction(new EventHandler<ActionEvent>() {
			public void handle(ActionEvent e) {
				 Prefs p = new Prefs();
				 FileChooser fileChooser = new FileChooser();
				 
	              //Set extension filter
	              FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("CUE Files (*.cue)", "*.cue");
	              fileChooser.getExtensionFilters().add(extFilter);
	              if (p.getCueLoc() != null) {
	            	  fileChooser.setInitialDirectory(new File(p.getCueLoc()));
	              }
	             
	              //Show open file dialog
	              File file = fileChooser.showOpenDialog(null);

	              if (file != null) {
	            	  File dir = file.getParentFile();
	            	  if (dir != null) {
	            		  p.setCueLoc(dir.getAbsolutePath());
	            	  }
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
	            		  _mp3Path = _cue.getPathToMP3();
	            		  File f = new File(_mp3Path);
	            		  _mp3File.setText(f.getName());
	            		  _cueFile = file;
	            	  } catch (Exception exp) {
	            		  exp.printStackTrace();
	            	  }
	              }	              
			}
		});
		MenuItem save = new MenuItem("Save Cue");
		save.setOnAction(new EventHandler<ActionEvent>() {
			public void handle(ActionEvent e) {
				save(_cueFile);
			}
		});
		
		MenuItem saveas = new MenuItem("Save Cue As");
		saveas.setOnAction(new EventHandler<ActionEvent>() {
			public void handle(ActionEvent e) {
				save(null);
			}
		});
		MenuItem quit = new MenuItem("Quit");
		quit.setOnAction(new EventHandler<ActionEvent>() {
			public void handle(ActionEvent e) {
				Prefs p = new Prefs();
				p.setX((int) stage.getX());
				p.setY((int) stage.getY());
				p.setWidth((int) stage.getWidth());
				p.setHeight((int) stage.getHeight());
				System.exit(0);
			}
		});
	    file.getItems().addAll(open, save, saveas, new SeparatorMenuItem(), quit);
	    bar.getMenus().addAll(file);
	    
	    _result = new TextArea();
	    
	    SplitPane splitp = new SplitPane();
	    splitp.setOrientation(Orientation.VERTICAL);
	    splitp.getItems().addAll(_table, _result);

		VBox box = new VBox();
		box.setSpacing(5);
		box.getChildren().addAll(bar, grid, hbox, splitp);
		

		{
			Prefs p = new Prefs();
			StackPane root = new StackPane();
			root.getChildren().addAll(box);
			stage.setScene(new Scene(root, p.width(), p.height()));
			stage.setX(p.x());
			stage.setY(p.y());
			stage.show();	
		}
		
		{
			Image icon = new Image(
					PCutMP3Gui.class.getResourceAsStream(
							String.format("/net/oesterholt/pcutmp3gui/resources/%s.png","pcutmp3")
					)
					);
    		stage.getIcons().add(icon);
		}
	}
	
	private void save(File cfile) {
		if (cfile == null) {
			 FileChooser fileChooser = new FileChooser();
			 
	         //Set extension filter
	         FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("CUE Files (*.cue)", "*.cue");
	         fileChooser.getExtensionFilters().add(extFilter);
	        
	         //Show open file dialog
	         File file = fileChooser.showSaveDialog(null);
	         cfile = file;
		}
		
		if (cfile != null) {
       	  try {
       		  _cue.setTitle(_title.getText().trim());
       		  _cue.setPerformer(_performer.getText().trim());
       		  File f = new File(_mp3Path);
       		  File d1 = f.getParentFile();
       		  File d2 = cfile.getParentFile();
       		  String p = _mp3Path;
       		  if (d1!=null && d1.equals(d2)) {
       			  p = f.getName();
       		  } 
       		  _cue.setPathToMP3(p);
       		  _cue.save(cfile);
	      } catch (Exception exp) {
	      	  exp.printStackTrace();
	      }
	    }
	}
	
	private void addBelow() {
		int row = _table.getSelectionModel().getSelectedIndex();
		if (row >= 0) {
			Track t = new Track();
			t.setPoint("01:00:00");			
			_cue.addTrack(row + 2, t);
		} else {
			Track t = new Track();
			t.setPoint("01:00:00");
			_cue.addTrack(_cue.getNumberTracks() + 1, t);
		}
	}
	
	private void insertBefore() {
		int row = _table.getSelectionModel().getSelectedIndex();
		if (row >= 0) {
			Track t = new Track();
			t.setPoint("01:00:00");
			_cue.addTrack(row + 1, t);
		}
	}
	
	private void delSelected() {
		int row = _table.getSelectionModel().getSelectedIndex();
		if (row >= 0) {
			_cue.removeTrack(row + 1);
		}
	}
	
	private void split() {
		save(_cueFile);
		_result.clear();
		new Thread(new Runnable() {
		    public void run() {
				//final StringBuffer sa = new StringBuffer();
				MainCLI cli = new MainCLI(new MainCLI.Report() {
					public void println(String msg) {
						final String M = msg;
						Platform.runLater(new Runnable() {
							public void run() {
								_result.appendText(M + "\n");
							}
						});
						//sa.append(msg);
						//sa.append("\n");
					}
				});
				String [] args = {  "--cue", _cueFile.getAbsolutePath(), 
									"--dir", _cueFile.getParentFile().getAbsolutePath(), 
									"--out", "%n_" + _performer.getText().trim() + "_" + _title.getText().trim() + "_%t" 
									};
				try {
					cli.run(args);
				} catch (IOException e) {
					e.printStackTrace();
				}
		    }
		}).start();
	}

	public static void main(String[] args) { 
		launch(args); 
	}
}
