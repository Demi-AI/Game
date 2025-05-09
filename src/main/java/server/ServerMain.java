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

            synchronized (clients) {
                if (clients.size() >= MAX_PLAYERS) {
                    System.out.println("已達到最大連線人數，拒絕新玩家的連線");
                    sendMessage(socket, new Message(Type.EXIT, "遊戲已滿，無法加入"));
                    socket.close();
                    continue;
                }

                ClientHandler handler = new ClientHandler(socket, clients.size() + 1);
                clients.add(handler);
                handler.start();
                System.out.println("玩家 " + clients.size() + " 已連線。");

                // 若達兩位玩家就啟動遊戲
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
                ready = true;

                send(new Message(Type.ASSIGN, playerId));

                while (true) {
                    Message msg = (Message) in.readObject();
                    if (msg.getType() == Type.EXIT) break;

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
            } finally {
                handlePlayerDisconnection();
            }
        }

        private void handlePlayerDisconnection() {
            synchronized (clients) {
                clients.remove(this);
                if (!clients.isEmpty()) {
                    ClientHandler remaining = clients.get(0);
                    remaining.send(new Message(Type.EXIT, "另一位玩家已斷線，遊戲結束，請重新連線"));
                }
                clients.clear();
                System.out.println("遊戲重置，等待玩家重新連線...");
            }
        }
    }
}
