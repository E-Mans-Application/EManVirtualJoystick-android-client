package com.emansapplication.emanvirtualjoystick;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A socket that dispatches the state of the gamepad to a dedicated server.
 */
public class SocketDispatcher implements Closeable {

    private final Thread _thread;
    private final AtomicBoolean _connected = new AtomicBoolean();
    private final AtomicBoolean _connecting = new AtomicBoolean();

    private final ConcurrentLinkedQueue<String> commands = new ConcurrentLinkedQueue<>();

    public SocketDispatcher(String host, int port) {
        _thread = new Thread() {
            @Override
            public void run() {
                main_loop(host, port);
            }
        };
        _thread.start();
    }

    private void main_loop(String host, int port) {
        _connected.set(false);
        _connecting.set(true);

        final Socket socket;
        try {
            socket = new Socket(host, port);
        } catch (IOException e) {
            e.printStackTrace();
            _connecting.set(false);
            return;
        }
        _connected.set(true);
        _connecting.set(false);

        PrintWriter output = null;
        BufferedReader input = null;
        try {

            socket.setSoTimeout(100);
            output = new PrintWriter(socket.getOutputStream());
            input = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            while (_connected.get() && !Thread.interrupted()) {
                try {
                    String command = input.readLine();
                    if ("QUIT".equals(command)) {
                        // Only "QUIT" is supported
                        break;
                    }
                } catch (SocketTimeoutException _ex) {
                    // Nothing to read
                }
                String cmd;
                while ((cmd = commands.poll()) != null) {
                    output.write(cmd);
                }
                if (output.checkError()) {
                    _connected.set(false);
                    break;
                }
            }

        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            _connected.set(false);

            if (input != null) {
                try {
                    input.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
            if (output != null) {
                output.write("QUIT\n");
                output.flush();
                output.close();
            }

            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    @Override
    public void close() {
        _connected.set(false);
        _thread.interrupt();
    }

    private void send(String command, int value) {
        commands.add(String.format(Locale.ROOT, "%s %d\n", command, value));
    }

    private void dispatchJoystick(String name_x, String name_y, double x, double y) {
        send(name_x, (int) (32768 * x));
        send(name_y, (int) (32768 * y));
    }

    public void dispatchLeftJoystickPosition(double x, double y) {
        dispatchJoystick("X", "Y", x, y);
    }

    public void dispatchRightJoystickPosition(double x, double y) {
        dispatchJoystick("RX", "RY", x, y);
    }

    public void dispatchStartButtonPressed() {
        send("BSTART", 1);
    }

    public void dispatchSelectButtonPressed() {
        send("BSELECT", 1);
    }

    public void dispatchStartButtonReleased() {
        send("BSTART", 0);
    }

    public void dispatchSelectButtonReleased() {
        send("BSELECT", 0);
    }

    @Deprecated
    public void ping() {
        send("READY", 0);
    }

    public boolean isConnected() {
        return _connected.get();
    }

    public boolean isConnecting() {
        return _connecting.get();
    }

}
