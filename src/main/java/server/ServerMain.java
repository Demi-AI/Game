package server;

import shared.Message;
import shared.Message.Type;

import java.io.*;
import java.net.*;
import java.util.*;

public class ServerMain {
    private static final int PORT = 5000;
    private static final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_PLAYERS = 2;

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("伺服器啟動，等待玩家連線...");

        while (true) {
            Socket socket = serverSocket.accept();

            // 先判斷是否已滿，不滿才加入
            if (clients.size() >= MAX_PLAYERS) {
                System.out.println("已達到最大連線人數，拒絕新玩家的連線");
                sendMessage(socket, new Message(Type.EXIT, "遊戲已經滿員，無法加入"));
                socket.close();
                continue;
            }

            ClientHandler handler = new ClientHandler(socket, clients.size());
            clients.add(handler);
            handler.start();
            System.out.println("玩家 " + clients.size() + " 已連線。");

            // 如果已經有兩位玩家就準備開始遊戲
            if (clients.size() == MAX_PLAYERS) {
                new Thread(() -> {
                    try {
                        while (clients.stream().anyMatch(c -> !c.isReady())) {
                            Thread.sleep(100);
                        }
                        broadcast(new Message(Type.START, "遊戲開始"));
                        System.out.println("已通知所有玩家：遊戲開始！");
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }).start();
            }
        }
    }

    public static void broadcast(Message msg) {
        synchronized (clients) {
            for (ClientHandler ch : clients) {
                ch.send(msg);
            }
        }
    }

    public static void sendMessage(Socket socket, Message msg) {
        try {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.writeObject(msg);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class ClientHandler extends Thread {
        private Socket socket;
        private ObjectInputStream in;
        private ObjectOutputStream out;
        private int playerId;
        private boolean ready = false;

        public ClientHandler(Socket socket, int playerId) {
            this.socket = socket;
            this.playerId = playerId;
        }

        public boolean isReady() {
            return ready;
        }

        public void send(Message msg) {
            try {
                out.writeObject(msg);
                out.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());
                ready = true; // 設為已準備好

                send(new Message(Type.ASSIGN, playerId));

                // 若遊戲已滿，等待遊戲開始
                synchronized (clients) {
                    if (clients.size() == MAX_PLAYERS) {
                        send(new Message(Type.START, "遊戲開始"));
                    }
                }

                while (true) {
                    Message msg = (Message) in.readObject();
                    if (msg.getType() == Type.EXIT)
                        break;
                    switch (msg.getType()) {
                        case MOVE:
                        case UNDO:
                        case RESTART:
                            broadcast(msg);
                            break;
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("玩家 " + playerId + " 離線");
                handlePlayerDisconnection();
            }
        }

        private void handlePlayerDisconnection() {
            // 玩家斷線後，通知另一位玩家結束遊戲並重設狀態
            synchronized (clients) {
                if (clients.size() == 1) {
                    // 如果只有一位玩家在線，結束遊戲並通知
                    ClientHandler remainingPlayer = clients.get(0);
                    remainingPlayer.send(new Message(Type.EXIT, "另一位玩家已斷線，遊戲結束，等待重連"));
                    clients.clear(); // 清空玩家，準備重新連線
                    System.out.println("遊戲結束，等待玩家重新連線");
                }
            }
        }
    }
}
