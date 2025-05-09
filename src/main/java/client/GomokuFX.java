package client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import shared.Message;
import shared.Message.Type;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class GomokuFX extends Application {
    final int BOARD_SIZE = 15, CELL_SIZE = 25;
    char[][] board = new char[BOARD_SIZE][BOARD_SIZE];
    List<int[]> moveHistory = new ArrayList<>();
    char currentPlayer = 'X';
    boolean gameOver = false;
    int myId;
    char myChar;

    ObjectOutputStream out;
    ObjectInputStream in;

    GraphicsContext gc;
    Image kittyImg, kuromiImg;
    ImageView turnImageView = new ImageView();
    Label turnLabel = new Label();
    int kittyScore = 0;
    int kuromiScore = 0;
    Label scoreLabel = new Label("Hello Kitty: 0 分 | Kuromi: 0 分");

    // 用來記錄上一局的勝者
    private char lastWinner = '\0';

    @Override
    public void start(Stage primaryStage) throws Exception {
        Socket socket = new Socket("localhost", 5000);
        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());

        kittyImg = new Image(getClass().getResourceAsStream("kitty.png"), 40, 40, true, true);
        kuromiImg = new Image(getClass().getResourceAsStream("Kuromi.png"), 40, 40, true, true);

        Canvas canvas = new Canvas(BOARD_SIZE * CELL_SIZE, BOARD_SIZE * CELL_SIZE);
        gc = canvas.getGraphicsContext2D();
        drawBoard();

        canvas.setOnMouseClicked(e -> {
            if (gameOver || currentPlayer != myChar)
                return;
            int col = (int) (e.getX() / CELL_SIZE);
            int row = (int) (e.getY() / CELL_SIZE);
            if (board[row][col] == '\0') {
                try {
                    out.writeObject(new Message(Type.MOVE, new int[] { row, col }));
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });

        Button undoBtn = new Button("悔棋");
        undoBtn.setOnAction(e -> {
            try {
                out.writeObject(new Message(Type.UNDO, null));
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });

        Button restartBtn = new Button("重新開始");
        restartBtn.setOnAction(e -> {
            try {
                out.writeObject(new Message(Type.RESTART, null));
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });

        Button exitBtn = new Button("結束遊戲");
        exitBtn.setOnAction((e) -> primaryStage.close());

        HBox buttonBox = new HBox(10, undoBtn, restartBtn, exitBtn);
        buttonBox.setAlignment(Pos.CENTER);

        HBox turnBox = new HBox(10, turnLabel, turnImageView);
        turnBox.setAlignment(Pos.CENTER);

        VBox root = new VBox(10, turnBox, canvas, scoreLabel, buttonBox);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(20)); 
        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("Hello Kitty vs Kuromi 五子棋");
        primaryStage.show();

        new Thread(this::listen).start();
    }

    private void listen() {
        try {
            Message msg;
            while ((msg = (Message) in.readObject()) != null) {
                switch (msg.getType()) {
                    case ASSIGN:
                        myId = (int) msg.getPayload();
                        myChar = (myId == 0) ? 'X' : 'O';
                        break;
                    case START:
                        Platform.runLater(() -> {
                            updateTurnLabel();
                        });
                        break;
                    case MOVE:
                        int[] move = (int[]) msg.getPayload();
                        Platform.runLater(() -> applyMove(move));
                        break;
                    case UNDO:
                        Platform.runLater(this::undoMove);
                        break;
                    case RESTART:
                        Platform.runLater(() -> {
                            resetBoard();
                            drawBoard();
                            // 根據上一局的勝者決定先手
                            if (lastWinner != '\0') {
                                currentPlayer = (lastWinner == 'X') ? 'X' : 'O';
                            } else {
                                currentPlayer = (myId == 0) ? 'X' : 'O';  // 第一局隨機先手
                            }
                            updateTurnLabel();
                        });
                        break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void applyMove(int[] move) {
        int row = move[0], col = move[1];
        if (board[row][col] != '\0')
            return;
        board[row][col] = currentPlayer;
        moveHistory.add(move);
        drawBoard();
        if (checkWin(row, col, currentPlayer)) {
            gameOver = true;
            showWinner(currentPlayer);
            // 記錄勝者
            lastWinner = currentPlayer;
        }
        currentPlayer = (currentPlayer == 'X') ? 'O' : 'X';
        updateTurnLabel();
    }

    void updateTurnLabel() {
        if (currentPlayer == 'X') {
            turnLabel.setText("輪到：Hello Kitty");
            turnImageView.setImage(kittyImg);
        } else {
            turnLabel.setText("輪到：Kuromi");
            turnImageView.setImage(kuromiImg);
        }
    }

    void drawBoard() {
        gc.setFill(Color.BEIGE);
        gc.fillRect(0, 0, BOARD_SIZE * CELL_SIZE, BOARD_SIZE * CELL_SIZE);
        gc.setStroke(Color.BLACK);
        for (int i = 0; i < BOARD_SIZE; i++) {
            gc.strokeLine(CELL_SIZE / 2, CELL_SIZE / 2 + i * CELL_SIZE,
                    CELL_SIZE / 2 + (BOARD_SIZE - 1) * CELL_SIZE, CELL_SIZE / 2 + i * CELL_SIZE);
            gc.strokeLine(CELL_SIZE / 2 + i * CELL_SIZE, CELL_SIZE / 2,
                    CELL_SIZE / 2 + i * CELL_SIZE, CELL_SIZE / 2 + (BOARD_SIZE - 1) * CELL_SIZE);
        }
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                double x = j * CELL_SIZE + CELL_SIZE / 2 - 16;
                double y = i * CELL_SIZE + CELL_SIZE / 2 - 16;
                if (board[i][j] == 'X')
                    gc.drawImage(kittyImg, x, y);
                else if (board[i][j] == 'O')
                    gc.drawImage(kuromiImg, x, y);
            }
        }
    }

    void resetBoard() {
        for (int i = 0; i < BOARD_SIZE; i++)
            Arrays.fill(board[i], '\0');
        moveHistory.clear();
        gameOver = false;
    }

    void undoMove() {
        if (!moveHistory.isEmpty()) {
            int[] last = moveHistory.remove(moveHistory.size() - 1);
            board[last[0]][last[1]] = '\0';
            currentPlayer = (currentPlayer == 'X') ? 'O' : 'X';
            drawBoard();
            updateTurnLabel();
        }
    }

    boolean checkWin(int r, int c, char p) {
        return count(r, c, 1, 0, p) + count(r, c, -1, 0, p) >= 4 ||
                count(r, c, 0, 1, p) + count(r, c, 0, -1, p) >= 4 ||
                count(r, c, 1, 1, p) + count(r, c, -1, -1, p) >= 4 ||
                count(r, c, 1, -1, p) + count(r, c, -1, 1, p) >= 4;
    }

    int count(int r, int c, int dr, int dc, char p) {
        int cnt = 0;
        for (int i = 1; i < 5; i++) {
            int nr = r + i * dr, nc = c + i * dc;
            if (nr < 0 || nr >= BOARD_SIZE || nc < 0 || nc >= BOARD_SIZE || board[nr][nc] != p)
                break;
            cnt++;
        }
        return cnt;
    }

    void showWinner(char p) {
        String name;
        if (p == 'X') {
            name = "Hello Kitty";
            kittyScore++;
        } else {
            name = "Kuromi";
            kuromiScore++;
        }

        scoreLabel.setText("Hello Kitty: " + kittyScore + " 分 | Kuromi: " + kuromiScore + " 分");

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText(name + " 獲勝！");
        alert.setContentText("恭喜！");
        alert.showAndWait();
    }
}
