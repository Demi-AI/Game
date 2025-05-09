package server;

import shared.Message;
import shared.Message.Type;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class ServerMain {
    private static final int PORT = 5000;
    private static List<ClientHandler> clients = new ArrayList<>();

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

        broadcast(new Message(Type.START, "遊戲開始"));
    }

    public static void broadcast(Message msg) {
        for (ClientHandler ch : clients) {
            ch.send(msg);
        }
    }

    static class ClientHandler extends Thread {
        private Socket socket;
        private ObjectInputStream in;
        private ObjectOutputStream out;
        private int playerId;

        public ClientHandler(Socket socket, int playerId) {
            this.socket = socket;
            this.playerId = playerId;
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

                send(new Message(Type.ASSIGN, playerId));

                while (true) {
                    Message msg = (Message) in.readObject();
                    if (msg.getType() == Type.EXIT)
                        break;
                    broadcast(msg);
                }
            } catch (Exception e) {
                System.out.println("玩家離線");
            }
        }
    }
}