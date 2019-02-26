import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.*;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import javafx.beans.property.SimpleStringProperty;
import javafx.scene.layout.VBox;

public class FileManager extends Application {

    private static HashMap<Integer, FileNode> fileNodes = new HashMap<>();

    private FileNode rootFileNode;
    private static TreeView<FileObj> treeView;

    private static final CopyPasteContext copyPasteContext = CopyPasteContext.getCopyPasteContext();
    private static final CommandHistory history = new CommandHistory();

    public static void main(String[] args) {
        Application.launch(args);
    }

    private void populateFileNodes(String rootFolderName) {


        List<FileObj> dirsToProcess = new ArrayList<>();
        List<FileObj> newDirsToProcess = new ArrayList<>();

        FileObj rootFileObj = new FolderFileObj(rootFolderName);
        rootFileNode = new FileNode(rootFileObj);
        fileNodes.put(rootFileObj.fileId, rootFileNode);
        dirsToProcess.add(rootFileObj);

        FileNode currentNode, newNode;
        FileObj currentFileObj;

        while (dirsToProcess.size() > 0) {
            newDirsToProcess.clear();
            for (FileObj fileObj : dirsToProcess) {
                File[] files = new File(fileObj.getFileName()).listFiles();
                currentNode = fileNodes.get(fileObj.fileId);
                fileNodes.put(fileObj.fileId, currentNode);
                assert files != null;
                for (File file : files) {
                    if (file.isDirectory()) {
                        currentFileObj = new FolderFileObj(file.getAbsolutePath());
                    } else {
                        currentFileObj = new SingleFileObj(file.getAbsolutePath());
                    }
                    currentNode.children.add(currentFileObj);
                    if (file.isDirectory()) {
                        newDirsToProcess.add(currentFileObj);
                        newNode = new FileNode(currentFileObj);
                        fileNodes.put(currentFileObj.fileId, newNode);
                    }
                }
            }
            dirsToProcess.clear();
            dirsToProcess.addAll(newDirsToProcess);
        }
    }

    private static void executeCommand(Command command) {
        if (command.execute()) {
            history.push(command);
        }
    }

    @SuppressWarnings("unused")
    private static void undo() {
        if (history.isEmpty()) return;
        Command command = history.pop();
        if (command != null) {
            command.undo();
        }
    }

    @Override
    public void start(Stage stage) {

        TextInputDialog dialog = new TextInputDialog("/home/villager/Music");
        dialog.setTitle("Folder Input");
        dialog.setHeaderText("Enter the full path to target folder");

        Optional<String> result = dialog.showAndWait();

        if (!result.isPresent()) {
            return;
        }

        String folderName = result.get();
        File homeFolder = new File(folderName);
        if (!homeFolder.exists()) {
            return;
        }

        populateFileNodes(folderName);

        List<FileNode> nodesToAdd = new ArrayList<>();
        List<FileNode> recruits = new ArrayList<>();
        nodesToAdd.add(rootFileNode);
        HashMap<Integer, TreeItem<FileObj>> treeItems = new HashMap<>();

        TreeItem<FileObj> rootNode = new TreeItem<>(rootFileNode.self);
        rootNode.setExpanded(true);

        treeItems.put(rootFileNode.self.fileId, rootNode);

        TreeItem<FileObj> savedNode, newNode;
        while (nodesToAdd.size() > 0) {
            recruits.clear();
            for (FileNode node : nodesToAdd) {
                savedNode = treeItems.get(node.self.fileId);
                treeItems.put(node.self.fileId, savedNode);
                for (FileObj file : node.children) {
                    newNode = new TreeItem<>(file);
                    savedNode.getChildren().add(newNode);
                    treeItems.put(file.fileId, newNode);
                    if (fileNodes.get(file.fileId) != null) {
                        recruits.add(fileNodes.get(file.fileId));
                    }
                }
            }
            nodesToAdd.clear();
            nodesToAdd.addAll(recruits);
        }

        stage.setTitle("Worobella File System");
        VBox box = new VBox();
        final Scene scene = new Scene(box, 400, 300);
        scene.setFill(Color.LIGHTGRAY);

        treeView = new TreeView<>(rootNode);
        treeView.setEditable(true);
        treeView.setCellFactory(p -> new TextFieldTreeCellImpl());

        box.getChildren().add(treeView);
        stage.setScene(scene);
        stage.show();
    }

    @SuppressWarnings("unused")
    public interface FileNodeIterator {

        boolean hasNext();

        FileObj getNext();

        void reset();

    }

    @SuppressWarnings("unused")
    public static class FileTreeIterator implements FileNodeIterator {

        private FileObj rootFolder;
        private int currentPosition = 0;
        private List<FileObj> fileObjects;

        FileTreeIterator(FileObj rootFolder) {
            this.rootFolder = rootFolder;
            loadList();
        }

        private void loadList() {
            fileObjects = new ArrayList<>();
            FileNode topNode = fileNodes.get(rootFolder.fileId);
            fileObjects.add(topNode.self);

            List<FileNode> nodesToProcess = new ArrayList<>();
            List<FileNode> temporaryNodes = new ArrayList<>();
            FileNode fileNode;
            nodesToProcess.add(topNode);
            File file;
            while (nodesToProcess.size() > 0) {
                temporaryNodes.clear();
                for (FileNode node : nodesToProcess) {
                    for (FileObj fileObj : node.children) {
                        file = new File(fileObj.getFileName());
                        if (file.isFile()) {
                            fileObjects.add(fileObj);
                        } else {
                            fileNode = fileNodes.get(fileObj.fileId);
                            if (fileNode != null) {
                                temporaryNodes.add(fileNode);
                            }
                        }
                    }
                }
                nodesToProcess.clear();
                nodesToProcess.addAll(temporaryNodes);
            }
        }

        @Override
        public boolean hasNext() {
            return currentPosition < fileObjects.size();
        }

        @Override
        public FileObj getNext() {
            return this.fileObjects.get(currentPosition++);
        }

        @Override
        public void reset() {
            currentPosition = 1;
        }
    }

    private final class TextFieldTreeCellImpl extends TreeCell<FileObj> {

        private TextField textField;
        private ContextMenu contextMenu = new ContextMenu();

        TextFieldTreeCellImpl() {

            MenuItem addFileMenuItem = new MenuItem("Add File");
            contextMenu.getItems().add(addFileMenuItem);
            addFileMenuItem.setOnAction(t -> executeCommand(new AddFileCommand(this)));

            MenuItem addDirMenuItem = new MenuItem("Add Directory");
            contextMenu.getItems().add(addDirMenuItem);
            addDirMenuItem.setOnAction(t -> executeCommand(new AddDirectoryCommand(this)));

            MenuItem copyMenuItem = new MenuItem("Copy file");
            contextMenu.getItems().add(copyMenuItem);
            copyMenuItem.setOnAction(t -> executeCommand(new CopyCommand(this)));

            MenuItem cutFileMenuItem = new MenuItem("Cut file");
            contextMenu.getItems().add(cutFileMenuItem);
            cutFileMenuItem.setOnAction(t -> executeCommand(new CutCommand(this)));

            MenuItem pasteFileMenu = new MenuItem("Paste file");
            contextMenu.getItems().add(pasteFileMenu);
            pasteFileMenu.setOnAction(t -> executeCommand(new PasteCommand(this)));

            MenuItem removeMenuItem = new MenuItem("Delete file");
            contextMenu.getItems().add(removeMenuItem);
            removeMenuItem.setOnAction(t -> executeCommand(new DeleteFileCommand(this)));

            MenuItem sizeMenuItem = new MenuItem("Compute Size");
            contextMenu.getItems().add(sizeMenuItem);
            sizeMenuItem.setOnAction(t -> executeCommand(new ComputeSizeCommand(this)));

        }

        @Override
        public void startEdit() {
            super.startEdit();
            if (textField == null) {
                createTextField();
            }
            setText(null);
            setGraphic(textField);
            textField.selectAll();
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            setText(getItem().getShortName());
            setGraphic(getTreeItem().getGraphic());
        }

        TextField getTextField() {
            return this.textField;
        }

        @Override
        public void updateItem(FileObj item, boolean empty) {
            super.updateItem(item, empty);

            if (empty) {
                setText(null);
                setGraphic(null);
            } else {
                if (isEditing()) {
                    if (textField != null) {
                        textField.setText(getString());
                    }
                    setText(null);
                    setGraphic(textField);
                } else {
                    setText(getString());
                    setGraphic(getTreeItem().getGraphic());
                    setContextMenu(contextMenu);
                }
            }
        }

        private void createTextField() {
            textField = new TextField(getString());
            textField.setOnKeyReleased(t -> {

                if (t.getCode() == KeyCode.ENTER) {

                    executeCommand(new RenameFileCommand(this));

                } else if (t.getCode() == KeyCode.ESCAPE) {
                    cancelEdit();
                }
            });

        }

        private String getString() {
            return getItem() == null ? "" : getItem().getShortName();
        }
    }

    @SuppressWarnings("WeakerAccess")
    public static abstract class FileObj {

        private SimpleStringProperty fileName;
        private SimpleStringProperty shortName;
        private int fileId;

        private static int counter = 0;

        private FileObj(String fileName) {
            this.fileName = new SimpleStringProperty(fileName);
            counter++;
            if (this.getFileName().contains("/")) {
                String[] split = this.getFileName().split("/");
                this.shortName = new SimpleStringProperty(
                        split[split.length - 1]
                );
            } else {
                this.shortName = this.fileName;
            }
            this.fileId = counter;
        }

        public String getFileName() {
            return fileName.get();
        }

        public void setShortName(String shortName) {
            if (this.getFileName().contains("/")) {
                String[] split = this.getFileName().split("/");
                split[split.length - 1] = shortName;
                this.fileName.set(String.join("/", split));
            } else {
                this.fileName.set(shortName);
            }
            this.shortName.set(shortName);
        }

        public String getShortName() {
            return this.shortName.get();
        }

        public void setFileName(String fileName) {
            this.fileName.set(fileName);
        }

        public abstract long computeSize();

    }

    public static class FolderFileObj extends FileObj {

        private FolderFileObj(String fileName) {
            super(fileName);
        }

        @Override
        public long computeSize() {
            long sizeTotal = 0;
            FileObj fileObj;
            File file;

            FileTreeIterator iterator = new FileTreeIterator(this);
            while (iterator.hasNext()) {
                fileObj = iterator.getNext();
                file = new File(fileObj.getFileName());
                sizeTotal += file.length();
            }
            return sizeTotal;
        }

        void copyFolderTo(File destDir) {

            File sourceDir = new File(this.getFileName());
            this.copyFolderHelper(sourceDir, destDir);
        }

        private void copyFolderHelper(File sourceDir, File destDir) {

            File[] items = sourceDir.listFiles();
            if (items != null && items.length > 0) {
                for (File anItem : items) {
                    if (anItem.isDirectory()) {
                        // create the directory in the destination
                        File newDir = new File(destDir, anItem.getName());
                        System.out.println("CREATED DIR: "
                                + newDir.getAbsolutePath());
                        //noinspection ResultOfMethodCallIgnored
                        newDir.mkdir();

                        // copy the directory (recursive call)
                        copyFolderHelper(anItem, newDir);
                    } else {
                        // copy the file
                        File destFile = new File(destDir, anItem.getName());
                        try {
                            SingleFileObj fileObj = new SingleFileObj(anItem.getAbsolutePath());
                            fileObj.copyFileTo(destFile);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    public static class SingleFileObj extends FileObj {

        private SingleFileObj(String fileName) {
            super(fileName);
        }

        @Override
        public long computeSize() {
            return new File(this.getFileName()).length();
        }

        void copyFileTo(File destFile) throws IOException {

            File sourceFile = new File(this.getFileName());
            System.out.println("COPY FILE: " + sourceFile.getAbsolutePath()
                    + " TO: " + destFile.getAbsolutePath());
            if (!destFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                destFile.createNewFile();
            }

            try (FileChannel sourceChannel = new FileInputStream(sourceFile).getChannel();
                 FileChannel destChannel = new FileOutputStream(destFile).getChannel()) {
                sourceChannel.transferTo(0, sourceChannel.size(), destChannel);
            }

        }
    }

    public static class FileNode {

        FileNode(FileObj self) {
            this.self = self;
            this.children = FXCollections.observableArrayList();
        }

        FileObj self;
        ObservableList<FileObj> children;

    }

    public static abstract class Command {

        @SuppressWarnings("WeakerAccess")
        protected TextFieldTreeCellImpl treeCell;
        @SuppressWarnings("WeakerAccess")
        protected CopyPasteContext context;

        Command(TextFieldTreeCellImpl treeCell) {
            this.treeCell = treeCell;
        }

        public abstract boolean execute();

        public abstract void undo();

        FileObj getItem() {
            return treeCell.getItem();
        }
    }

    public static class CommandHistory {

        private Stack<Command> history = new Stack<>();

        void push(Command c) {
            this.history.push(c);
        }

        Command pop() {
            return history.pop();
        }

        boolean isEmpty() {
            return history.isEmpty();
        }

    }

    public static class ComputeSizeCommand extends Command {


        ComputeSizeCommand(TextFieldTreeCellImpl treeCell) {
            super(treeCell);
            this.context = copyPasteContext.getClone();
        }

        @Override
        public boolean execute() {

            TreeItem c = treeView.getSelectionModel().getSelectedItem();
            FileObj file = (FileObj) c.getValue();
            long totalSize = file.computeSize();
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Size Information");
            alert.setHeaderText(null);
            alert.setContentText("Total size of " + file.getShortName() + " : " +
                    totalSize + " Bytes.");
            alert.showAndWait();

            return true;
        }

        @Override
        public void undo() {
            // TODO
        }
    }

    private static void deleteFileNode(TreeItem<FileObj> c) {
        c.getParent().getChildren().remove(c);
        FileObj fileObj = c.getValue();
        fileNodes.remove(fileObj.fileId);
    }

    public static class DeleteFileCommand extends Command {

        DeleteFileCommand(TextFieldTreeCellImpl treeCell) {
            super(treeCell);
            this.context = copyPasteContext.getClone();
        }

        @Override
        public boolean execute() {

            File file = new File(getItem().getFileName());
            if (file.delete()) {
                System.out.println("The file was deleted");
            } else {
                System.out.println("Could not delete the file");
            }
            TreeItem<FileObj> c = treeView.getSelectionModel().getSelectedItem();
            deleteFileNode(c);
            return true;
        }

        @Override
        public void undo() {
            // TODO
        }
    }

    public static class RenameFileCommand extends Command {

        RenameFileCommand(TextFieldTreeCellImpl treeCell) {
            super(treeCell);
            this.context = copyPasteContext.getClone();
        }

        @Override
        public boolean execute() {

            FileObj fileObj = this.treeCell.getItem();
            TextField input = this.treeCell.getTextField();

            String oldFullFileName = fileObj.getFileName();
            File oldName = new File(oldFullFileName);
            fileObj.setShortName(input.getText());
            String newFullFileName = fileObj.getFileName();
            File newName = new File(newFullFileName);

            boolean renamed = oldName.renameTo(newName);

            if (renamed) {
                System.out.println("The file was renamed");
            } else {
                System.out.println("Could not rename");
            }

            fileObj.setShortName(input.getText());
            this.treeCell.commitEdit(fileObj);

            return true;
        }

        @Override
        public void undo() {
            // TODO
        }
    }

    public static class AddFileCommand extends Command {


        AddFileCommand(TextFieldTreeCellImpl treeCell) {
            super(treeCell);
            this.context = copyPasteContext.getClone();
        }

        @Override
        public boolean execute() {

            try {

                FileObj fileObj = getItem();

                String newFullFileName = fileObj.getFileName().concat("/unknown.txt");
                File newFile = new File(newFullFileName);
                boolean created = newFile.createNewFile();
                if (created) {
                    System.out.println("The file has been created");
                }
                FileObj newFileObj = new SingleFileObj(newFullFileName);
                addFileNode(fileObj, newFileObj, treeCell);

            } catch (IOException ex) {
                System.err.println(ex.getMessage());
            }

            return true;
        }

        @Override
        public void undo() {
            // TODO
        }
    }

    private static void addFileNode(FileObj parentFileObj, FileObj childFileObj, TreeCell<FileObj> parentTreeCell) {
        FileNode fileNode = fileNodes.get(parentFileObj.fileId);
        if (fileNode == null) {
            fileNode = new FileNode(parentFileObj);
            fileNodes.put(parentFileObj.fileId, fileNode);
        }
        fileNodes.get(parentFileObj.fileId).children.add(childFileObj);
        TreeItem<FileObj> newFileTreeItem =
                new TreeItem<>(childFileObj);
        ObservableList<TreeItem<FileObj>> children = parentTreeCell.getTreeItem().getChildren();
        children.add(newFileTreeItem);
    }

    public static class AddDirectoryCommand extends Command {

        AddDirectoryCommand(TextFieldTreeCellImpl treeCell) {
            super(treeCell);
            this.context = copyPasteContext.getClone();
        }

        @Override
        public boolean execute() {

            FileObj fileObj = getItem();

            String newFullFileName = fileObj.getFileName().concat("/New Folder");
            File newDir = new File(newFullFileName);
            if (newDir.mkdir()) {
                System.out.println("Directory created");
            } else {
                System.out.println("Directory not created");
            }
            FileObj newFileObj = new FolderFileObj(newFullFileName);
            FileNode fileNode = fileNodes.get(fileObj.fileId);
            this.context.setDestnFileObj(newFileObj);
            if (fileNode == null) {
                fileNode = new FileNode(fileObj);
                fileNodes.put(fileObj.fileId, fileNode);
            }
            fileNodes.get(fileObj.fileId).children.add(newFileObj);
            TreeItem<FileObj> newFileTreeItem =
                    new TreeItem<>(newFileObj);
            this.treeCell.getTreeItem().getChildren().add(newFileTreeItem);
            return true;
        }

        @Override
        public void undo() {
            // TODO
        }
    }

    public static class CopyCommand extends Command {


        CopyCommand(TextFieldTreeCellImpl treeCell) {
            super(treeCell);
        }

        @Override
        public boolean execute() {

            copyPasteContext.setOriginFileObj(this.treeCell.getItem());
            copyPasteContext.setPastePending(true);
            copyPasteContext.setActionType(CopyPasteContext.Actions.COPY);

            return true;
        }

        @Override
        public void undo() {
            copyPasteContext.setPastePending(false);
        }
    }

    public static class CutCommand extends Command {

        CutCommand(TextFieldTreeCellImpl treeCell) {
            super(treeCell);
        }

        @Override
        public boolean execute() {

            copyPasteContext.setOriginFileObj(this.treeCell.getItem());
            copyPasteContext.setPastePending(true);
            copyPasteContext.setActionType(CopyPasteContext.Actions.CUT);
            copyPasteContext.setOriginTreeCell(this.treeCell);

            return true;
        }

        @Override
        public void undo() {
            copyPasteContext.setPastePending(false);
        }
    }

    public static class PasteCommand extends Command {


        PasteCommand(TextFieldTreeCellImpl treeCell) {
            super(treeCell);
            this.context = copyPasteContext.getClone();
        }

        @Override
        public boolean execute() {

            if (context.isPastePending()) {

                FileObj originFileObj = context.getOriginFileObj();
                FileObj destnFileObj = this.treeCell.getItem();
                FileObj childFileObj;
                this.context.setDestnFileObj(destnFileObj);

                File sourceFile = new File(originFileObj.getFileName());
                File destnFile = new File(destnFileObj.getFileName());

                if (destnFile.isDirectory()) {
                    String newFileName = destnFileObj.getFileName() + "/" + originFileObj.getShortName();
                    childFileObj = new SingleFileObj(newFileName);
                    destnFileObj.setFileName(newFileName);
                    destnFile = new File(destnFileObj.getFileName());
                } else {
                    childFileObj = originFileObj;
                }
                if (sourceFile.isFile()) {
                    try {
                        ((SingleFileObj) originFileObj).copyFileTo(destnFile);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    ((FolderFileObj) originFileObj).copyFolderTo(destnFile);

                }

                if (this.context.getActionType() == CopyPasteContext.Actions.CUT) {
                    System.out.println("YEAH CONTEXT IS CUT");
                    if (sourceFile.delete()) {
                        System.out.println("The file was cut and pasted");
                        deleteFileNode(this.context.getOriginTreeCell().getTreeItem());
                    } else {
                        System.out.println("Could not be cut and pasted");
                    }
                }
                addFileNode(destnFileObj, childFileObj, treeCell);

            }
            return true;
        }

        @Override
        public void undo() {

        }
    }

    public static class CopyPasteContext {

        private FileObj originFileObj, destnFileObj;
        private boolean isPastePending = false;
        private Actions actionType;
        private TreeCell<FileObj> originTreeCell = null;

        private static CopyPasteContext context;

        FileObj getOriginFileObj() {
            return originFileObj;
        }

        void setOriginFileObj(FileObj originFileObj) {
            this.originFileObj = originFileObj;
        }

        boolean isPastePending() {
            return isPastePending;
        }

        void setPastePending(boolean pastePending) {
            isPastePending = pastePending;
        }

        Actions getActionType() {
            return actionType;
        }

        void setActionType(Actions actionType) {
            this.actionType = actionType;
        }

        void setDestnFileObj(FileObj destnFileObj) {
            this.destnFileObj = destnFileObj;
        }

        void setOriginTreeCell(TreeCell<FileObj> originTreeCell) {
            this.originTreeCell = originTreeCell;
        }

        TreeCell<FileObj> getOriginTreeCell() {
            return originTreeCell;
        }

        public enum Actions {
            COPY, CUT
        }

        CopyPasteContext getClone() {
            CopyPasteContext context = new CopyPasteContext();
            context.setPastePending(this.isPastePending);
            context.setOriginFileObj(this.originFileObj);
            context.setDestnFileObj(this.destnFileObj);
            context.setActionType(this.actionType);
            context.setOriginTreeCell(this.originTreeCell);
            return context;
        }

        static CopyPasteContext getCopyPasteContext() {
            if (context == null) {
                context = new CopyPasteContext();
            }
            return context;
        }

    }

}
