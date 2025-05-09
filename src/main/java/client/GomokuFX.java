package client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import shared.Message;
import shared.Message.Type;

import java.io.*;
import java.net.Socket;
import java.util.Stack;

public class GomokuFX extends Application {
    final int SIZE = 15, CELL_SIZE = 40;
    char[][] board = new char[SIZE][SIZE];
    char myChar = 'X';
    char currentPlayer = 'X';
    boolean gameOver = false;
    boolean myTurn = false;
    Stack<int[]> moveHistory = new Stack<>();
    ObjectOutputStream out;
    ObjectInputStream in;

    Image kittyImg, kuromiImg;
    ImageView turnImageView = new ImageView();
    Label turnLabel = new Label();
    Label statusLabel = new Label("等待對手連線...");

    @Override
    public void start(Stage primaryStage) throws Exception {
        kittyImg = new Image("file:kitty.png", 40, 40, true, true);
        kuromiImg = new Image("file:Kuromi.png", 40, 40, true, true);

        Canvas canvas = new Canvas(SIZE * CELL_SIZE, SIZE * CELL_SIZE);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        drawBoard(gc);

        turnLabel.setText("輪到：Hello Kitty");
        turnImageView.setImage(kittyImg);
        turnImageView.setFitWidth(32);
        turnImageView.setFitHeight(32);

        HBox turnBox = new HBox(10, turnLabel, turnImageView);
        turnBox.setAlignment(Pos.CENTER);

        canvas.setOnMouseClicked(e -> {
            if (!myTurn || gameOver)
                return;
            int col = (int) (e.getX() / CELL_SIZE);
            int row = (int) (e.getY() / CELL_SIZE);
            if (board[row][col] != '\0')
                return;

            board[row][col] = myChar;
            moveHistory.push(new int[] { row, col });
            drawBoard(gc);
            myTurn = false;
            sendMessage(new Message(Type.MOVE, new int[] { row, col }));

            if (isWin(row, col, myChar)) {
                gameOver = true;
                showAlert("你獲勝了！");
                sendMessage(new Message(Type.SYSTEM, "對手已輸"));
            }
        });

        VBox root = new VBox(10, turnBox, canvas, statusLabel);
        root.setAlignment(Pos.CENTER);
        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("五子棋線上對戰");
        primaryStage.show();

        new Thread(this::setupConnection).start();
    }

    void setupConnection() {
        try {
            Socket socket = new Socket("localhost", 5000);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            while (true) {
                Message msg = (Message) in.readObject();
                switch (msg.getType()) {
                    case ASSIGN:
                        int id = (Integer) msg.getPayload();
                        myChar = (id == 0) ? 'X' : 'O';
                        Platform.runLater(
                                () -> statusLabel.setText("你是 " + (myChar == 'X' ? "Hello Kitty" : "Kuromi")));
                        break;
                    case START:
                        Platform.runLater(() -> {
                            myTurn = (myChar == 'X');
                            statusLabel.setText("遊戲開始！");
                        });
                        break;
                    case MOVE:
                        int[] move = (int[]) msg.getPayload();
                        Platform.runLater(() -> {
                            board[move[0]][move[1]] = (myChar == 'X') ? 'O' : 'X';
                            drawBoard(((Canvas) ((VBox) statusLabel.getParent()).getChildren().get(1))
                                    .getGraphicsContext2D());
                            myTurn = true;
                        });
                        break;
                    case SYSTEM:
                        Platform.runLater(() -> {
                            gameOver = true;
                            showAlert("你輸了！");
                        });
                        break;
                }
            }
        } catch (Exception e) {
            Platform.runLater(() -> {
                showAlert("連線失敗: " + e.getMessage());
                e.printStackTrace();
            });
        }
    }

    void sendMessage(Message msg) {
        try {
            out.writeObject(msg);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void drawBoard(GraphicsContext gc) {
        gc.setFill(Color.BEIGE);
        gc.fillRect(0, 0, SIZE * CELL_SIZE, SIZE * CELL_SIZE);
        gc.setStroke(Color.BLACK);

        for (int i = 0; i < SIZE; i++) {
            gc.strokeLine(CELL_SIZE / 2, CELL_SIZE / 2 + i * CELL_SIZE,
                    CELL_SIZE / 2 + (SIZE - 1) * CELL_SIZE, CELL_SIZE / 2 + i * CELL_SIZE);
            gc.strokeLine(CELL_SIZE / 2 + i * CELL_SIZE, CELL_SIZE / 2,
                    CELL_SIZE / 2 + i * CELL_SIZE, CELL_SIZE / 2 + (SIZE - 1) * CELL_SIZE);
        }

        for (int i = 0; i < SIZE; i++)
            for (int j = 0; j < SIZE; j++) {
                double x = j * CELL_SIZE + CELL_SIZE / 2 - 16;
                double y = i * CELL_SIZE + CELL_SIZE / 2 - 16;
                if (board[i][j] == 'X')
                    gc.drawImage(kittyImg, x, y);
                else if (board[i][j] == 'O')
                    gc.drawImage(kuromiImg, x, y);
            }
    }

    boolean isWin(int row, int col, char player) {
        return check(row, col, player, 1, 0) || check(row, col, player, 0, 1) ||
                check(row, col, player, 1, 1) || check(row, col, player, 1, -1);
    }

    boolean check(int row, int col, char player, int dx, int dy) {
        int count = 1;
        for (int i = 1; i < 5; i++) {
            int r = row + i * dx, c = col + i * dy;
            if (r < 0 || r >= SIZE || c < 0 || c >= SIZE || board[r][c] != player)
                break;
            count++;
        }
        for (int i = 1; i < 5; i++) {
            int r = row - i * dx, c = col - i * dy;
            if (r < 0 || r >= SIZE || c < 0 || c >= SIZE || board[r][c] != player)
                break;
            count++;
        }
        return count >= 5;
    }

    void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("遊戲結果");
        alert.setHeaderText(msg);
        alert.showAndWait();
    }
}