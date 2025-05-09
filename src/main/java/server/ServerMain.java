package server;

import shared.Message;
import shared.Message.Type;

import java.io.*;
import java.net.*;
import java.util.*;

public class ServerMain {
    private static final int PORT = 5000;
    private static final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("伺服器啟動，等待玩家連線...");

        while (clients.size() < 2) {
            Socket socket = serverSocket.accept();
            ClientHandler handler = new ClientHandler(socket, clients.size());
            clients.add(handler);
            handler.start();
            System.out.println("玩家 " + clients.size() + " 已連線。");
        }

        // 確保所有 handler 都完成初始化後才送出 START
        new Thread(() -> {
            try {
                // 等待所有 handler 都準備好 output stream
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

    public static void broadcast(Message msg) {
        synchronized (clients) {
            for (ClientHandler ch : clients) {
                ch.send(msg);
            }
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
            } catch (Exception e) {
                System.out.println("玩家 " + playerId + " 離線");
            }
        }
    }
}
