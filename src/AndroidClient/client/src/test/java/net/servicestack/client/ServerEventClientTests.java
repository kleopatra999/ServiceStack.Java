package net.servicestack.client;

import junit.framework.TestCase;

import net.servicestack.client.sse.ServerEventConnect;
import net.servicestack.client.sse.ServerEventJoin;
import net.servicestack.client.sse.ServerEventLeave;
import net.servicestack.client.sse.ServerEventMessage;
import net.servicestack.client.sse.ServerEventsClient;
import net.servicestack.client.sse.SingletonInstanceResolver;
import net.servicestack.func.Func;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static chat.chatdtos.*;
import static chat.chatdtos.ChatMessage;
import static chat.chatdtos.PostChatToChannel;
import static chat.chatdtos.ResetServerEvents;

/**
 * Created by mythz on 2/10/2017.
 */

public class ServerEventClientTests extends TestCase {

    public ServerEventClientTests() {
        Log.setInstance(new LogProvider("", true));
    }

    public void test_Can_connect_to_ServerEventsStream() throws Exception {
        final CountDownLatch signal = new CountDownLatch(1);

        try (ServerEventsClient client = new ServerEventsClient("http://chat.servicestack.net", "home")
                .setOnConnect(e -> {
                    System.out.print("onConnect: " + e);
                    signal.countDown();
                }).start())
        {
            assertTrue(signal.await(5, TimeUnit.SECONDS));
        }
    }

    public void test_Does_fire_onJoin_events() throws Exception {
        final CountDownLatch signal = new CountDownLatch(1);

        try(ServerEventsClient client = new ServerEventsClient("http://chat.servicestack.net", "home"))
        {
            client
                .setOnConnect(e -> {
                    assertTrue(e.getHeartbeatUrl().startsWith("http://chat.servicestack.net"));
                })
                .setOnCommand(e -> {
                    System.out.print("onCommand: " + e);
                    assertTrue(e instanceof ServerEventJoin);
                    ServerEventJoin joinMsg = (ServerEventJoin)e;
                    assertEquals(client.getConnectionInfo().getDisplayName(), joinMsg.getDisplayName());
                    signal.countDown();
                })
                .start();

            assertTrue(signal.await(5, TimeUnit.SECONDS));
        }
    }

    public void test_Does_fire_onJoin_events_for_multiple_Channels() throws Exception {
        final CountDownLatch signal = new CountDownLatch(1);

        String[] channels = new String[] { "A", "B", "C" };
        List<ServerEventJoin> joinMsgs = new ArrayList<>();

        try (ServerEventsClient client = new ServerEventsClient("http://chat.servicestack.net", channels))
        {
            client
                .setOnCommand(e -> {
                    System.out.print("onCommand: " + e);

                    if (e instanceof ServerEventJoin) {
                        ServerEventJoin joinMsg = (ServerEventJoin)e;
                        joinMsgs.add(joinMsg);

                        assertEquals(channels[joinMsgs.size() - 1], joinMsg.getChannel());
                        assertEquals(client.getConnectionInfo().getDisplayName(), joinMsg.getDisplayName());

                        if (joinMsgs.size() == channels.length)
                            signal.countDown();
                    }
                })
                .start();

            assertTrue(signal.await(5, TimeUnit.SECONDS));
        }
    }

    private void clearPreviousRun(String[] channels) throws Exception {
        final CountDownLatch signal = new CountDownLatch(1);

        try (ServerEventsClient client = new ServerEventsClient("http://chat.servicestack.net", channels)
                .setOnConnect(e -> {
                    signal.countDown();
                })
                .start())
        {
            assertTrue(signal.await(5, TimeUnit.SECONDS));
        }
    }

    public void ignore_test_ClearPreviousRun() throws Exception {
        clearPreviousRun(null);
    }

    public void test_Does_fire_all_callbacks() throws Exception {
//        clearPreviousRun(null);

        List<ServerEventConnect> connectMsgs = new ArrayList<>();
        List<ServerEventMessage> msgs = new ArrayList<>();
        List<ServerEventMessage> commands = new ArrayList<>();
        List<Exception> errors = new ArrayList<>();

        try(ServerEventsClient client1 = new ServerEventsClient("http://chat.servicestack.net")
            .setOnConnect(connectMsgs::add)
            .setOnCommand(commands::add)
            .setOnMessage(msgs::add)
            .setOnException(errors::add)
            .start())
        {
            while (connectMsgs.size() < 1 || commands.size() < 1) {
                Thread.sleep(100);
            }

            ServerEventConnect connectMsg = Func.first(connectMsgs);
            ServerEventJoin joinMsg = Func.first(Func.ofType(commands, ServerEventJoin.class));
            assertNotNull(connectMsg);
            assertNotNull(joinMsg);

            assertEquals(0, msgs.size());
            assertEquals(0, errors.size());
            assertEquals(1, commands.size()); //join

            connectMsgs.clear();
            commands.clear();

            try(ServerEventsClient client2 = new ServerEventsClient("http://chat.servicestack.net")
                .setOnConnect(connectMsgs::add)
                .start())
            {
                while (connectMsgs.size() < 1 || commands.size() < 1) {
                    Thread.sleep(100);
                }

                connectMsg = Func.first(connectMsgs);
                joinMsg = Func.first(Func.ofType(commands, ServerEventJoin.class));

                Utils.readToEnd(connectMsg.getUnRegisterUrl());
            }

            while (commands.size() < 2) {
                Thread.sleep(100);
            }

            ServerEventLeave leaveMsg = Func.first(Func.ofType(commands, ServerEventLeave.class));

            assertNotNull(joinMsg);
            assertNotNull(leaveMsg);
            assertTrue(commands.size() >= 2);
            assertEquals(errors.size(), 0);
        }
    }

    private void postChat(ServerEventsClient client, String message) {
        postChat(client, message, null);
    }

    private void postChat(ServerEventsClient client, String message, String channel) {
        client.getServiceClient().post(new PostChatToChannel()
            .setFrom(client.getSubscriptionId())
            .setMessage(message)
            .setChannel(channel != null ? channel : ServerEventsClient.UnknownChannel)
            .setSelector("cmd.chat"));
    }

    private void postRaw(ServerEventsClient client, String selector, String message) {
        postRaw(client, selector, message, null);
    }

    private void postRaw(ServerEventsClient client, String selector, String message, String channel) {
        client.getServiceClient().post(new PostRawToChannel()
            .setFrom(client.getSubscriptionId())
            .setMessage(message)
            .setChannel(channel != null ? channel : ServerEventsClient.UnknownChannel)
            .setSelector(selector));
    }

    private void postObject(ServerEventsClient client, CustomType dto){
        postObject(client, dto, null, null);
    }

    private void postObject(ServerEventsClient client, CustomType dto, String selector){
        postObject(client, dto, selector, null);
    }

    private void postObject(ServerEventsClient client, CustomType dto, String selector, String channel){
        client.getServiceClient().post(new PostObjectToChannel()
                .setCustomType(dto)
                .setChannel(channel != null ? channel : ServerEventsClient.UnknownChannel)
                .setSelector(selector));
    }

    private void postObject(ServerEventsClient client, SetterType dto){
        client.getServiceClient().post(new PostObjectToChannel()
                .setSetterType(dto)
                .setChannel(ServerEventsClient.UnknownChannel));
    }

    public void test_Does_receive_messages() throws Exception {

        List<ServerEventConnect> connectMsgs = new ArrayList<>();
        List<ServerEventMessage> commands = new ArrayList<>();
        List<ServerEventMessage> msgs1 = new ArrayList<>();
        List<ServerEventMessage> msgs2 = new ArrayList<>();

        try (
            ServerEventsClient client1 = new ServerEventsClient("http://chat.servicestack.net")
                .setOnConnect(connectMsgs::add)
                .setOnCommand(commands::add)
                .setOnMessage(msgs1::add);

            ServerEventsClient client2 = new ServerEventsClient("http://chat.servicestack.net")
                .setOnConnect(connectMsgs::add)
                .setOnMessage(msgs2::add))
        {
            client1.start();

            while (connectMsgs.size() < 1 || commands.size() < 1) {
                Thread.sleep(100);
            }

            client2.start();

            while (connectMsgs.size() < 2 || commands.size() < 2) {
                Thread.sleep(100);
            }

            ServerEventConnect info1 = connectMsgs.get(0);
            postChat(client1, "hello from client1");

            while (msgs1.size() < 1 || msgs2.size() < 1) {
                Thread.sleep(100);
            }

            ServerEventMessage msg1 = Func.first(msgs1);
            ServerEventMessage msg2 = Func.first(msgs2);

            assertTrue(msg1.getEventId() > 0);
            assertTrue(msg2.getEventId() > 0);
            assertEquals("cmd.chat", msg1.getSelector());
            assertEquals("cmd.chat", msg2.getSelector());

            ChatMessage chatMsg1 = JsonUtils.fromJson(msg1.getJson(), ChatMessage.class);
            ChatMessage chatMsg2 = JsonUtils.fromJson(msg2.getJson(), ChatMessage.class);

            for (ChatMessage chatMsg : new ChatMessage[]{ chatMsg1, chatMsg2 }){
                assertTrue(chatMsg.getId() > 0);
                assertEquals(info1.getUserId(), chatMsg.getFromUserId());
                assertEquals(info1.getDisplayName(), chatMsg.getFromName());
                assertEquals(info1.getDisplayName(), chatMsg.getFromName());
            }

            assertEquals(1, msgs1.size());
            assertEquals(1, msgs2.size());

            ServerEventConnect info2 = connectMsgs.get(1);
            postChat(client2, "hello from client2");

            while (msgs1.size() < 2 || msgs2.size() < 2) {
                Thread.sleep(100);
            }

            msg1 = msgs1.get(1);
            msg2 = msgs2.get(1);

            chatMsg1 = JsonUtils.fromJson(msg1.getJson(), ChatMessage.class);
            chatMsg2 = JsonUtils.fromJson(msg2.getJson(), ChatMessage.class);

            for (ChatMessage chatMsg : new ChatMessage[]{ chatMsg1, chatMsg2 }){
                assertTrue(chatMsg.getId() > 0);
                assertEquals(info2.getUserId(), chatMsg.getFromUserId());
                assertEquals(info2.getDisplayName(), chatMsg.getFromName());
                assertEquals(info2.getDisplayName(), chatMsg.getFromName());
            }

            assertEquals(2, msgs1.size());
            assertEquals(2, msgs2.size());
        }
    }

    public void test_Does_send_multiple_heartbeats() throws Exception {
        final CountDownLatch signal = new CountDownLatch(1);

        List<ServerEventMessage> heartbeats = new ArrayList<>();
        try(ServerEventsClient client1 = new ServerEventsClient("http://chat.servicestack.net")
            .setOnConnect(e -> e.setHeartbeatIntervalMs(1000)) //change to 1s
            .setOnHeartbeat(e -> {
                heartbeats.add(e);
                if (heartbeats.size() >= 3)
                    signal.countDown();
            })
            .start()) {

            assertTrue(signal.await(5, TimeUnit.SECONDS));

            assertTrue(heartbeats.size() >= 3);
        }
    }

    public void test_Does_reconnect_on_lost_connection() throws Exception {

        List<ServerEventConnect> connectMsgs = new ArrayList<>();
        List<ServerEventMessage> msgs1 = new ArrayList<>();
        try(ServerEventsClient client1 = new ServerEventsClient("http://chat.servicestack.net")
            .setOnConnect(connectMsgs::add)
            .setOnMessage(msgs1::add)
            .start()) {

            while (connectMsgs.size() < 1){
                Thread.sleep(100);
            }

            postChat(client1, "msg1 from client1");

            while (msgs1.size() < 1){
                Thread.sleep(100);
            }

            client1.getServiceClient().post(new ResetServerEvents());

            try(ServerEventsClient client2 = new ServerEventsClient("http://chat.servicestack.net")
                .setOnConnect(connectMsgs::add)
                .start()) {

                while (connectMsgs.size() < 3){ //client1 + client1 reconnect + client2
                    Thread.sleep(100);
                }

                postChat(client2, "msg2 from client2");

                while (msgs1.size() < 2){ //msg1 + msg2
                    Thread.sleep(100);
                }
            }

            ServerEventMessage msg2 = msgs1.get(1);

            ChatMessage chatMsg2 = JsonUtils.fromJson(msg2.getJson(), ChatMessage.class);

            assertEquals("msg2 from client2", chatMsg2.getMessage());
        }
    }

    public void test_Does_send_message_to_Handler() throws Exception {

        List<ChatMessage> chatMsgs = new ArrayList<>();

        try(ServerEventsClient client1 = new ServerEventsClient("http://chat.servicestack.net")
                .registerHandler("chat", (client, e) -> {
                    ChatMessage chatMsg = JsonUtils.fromJson(e.getJson(), ChatMessage.class);
                    chatMsgs.add(chatMsg);
                })
                .start()
                .waitTillConnected()) {

            postChat(client1, "msg1");

            while (chatMsgs.size() < 1){
                Thread.sleep(100);
            }

            ChatMessage chatMsg = Func.last(chatMsgs);
            assertEquals("msg1", chatMsg.getMessage());

            postChat(client1, "msg2");

            while (chatMsgs.size() < 2){
                Thread.sleep(100);
            }

            chatMsg = Func.last(chatMsgs);
            assertEquals("msg2", chatMsg.getMessage());
        }
    }

    public void test_Does_send_message_to_named_receiver() throws Exception {
        List<ServerEventMessage> msgs1 = new ArrayList<>();

        try(ServerEventsClient client1 = new ServerEventsClient("http://chat.servicestack.net")
                .registerNamedReceiver("test", TestNamedReceiver.class)
                .setOnMessage(msgs1::add)
                .start()
                .waitTillConnected()) {

            postObject(client1, new CustomType()
                .setId(1)
                .setName("Foo"), "test.FooMethod");

            while (msgs1.size() < 1){
                Thread.sleep(100);
            }

            CustomType foo = TestNamedReceiver.FooMethodReceived;
            assertNotNull(foo);
            assertEquals(1, foo.getId().intValue());
            assertEquals("Foo", foo.getName());

            postObject(client1, new CustomType()
                .setId(2)
                .setName("Bar"), "test.BarMethod");

            while (msgs1.size() < 2){
                Thread.sleep(100);
            }

            CustomType bar = TestNamedReceiver.BarMethodReceived;
            assertNotNull(bar);
            assertEquals(2, bar.getId().intValue());
            assertEquals("Bar", bar.getName());

            postObject(client1, new CustomType()
                .setId(3)
                .setName("Baz"), "test.BazMethod");

            while (msgs1.size() < 3){
                Thread.sleep(100);
            }

            CustomType baz = TestNamedReceiver.NoSuchMethodReceived;
            assertNotNull(baz);
            assertEquals(3, baz.getId().intValue());
            assertEquals("Baz", baz.getName());
            assertEquals("BazMethod", TestNamedReceiver.NoSuchMethodSelector);

            postObject(client1, new CustomType()
                .setId(4)
                .setName("Qux"), "test.QuxSetter");

            while (msgs1.size() < 4){
                Thread.sleep(100);
            }

            CustomType qux = TestNamedReceiver.QuxSetterReceived;
            assertNotNull(qux);
            assertEquals(4, qux.getId().intValue());
            assertEquals("Qux", qux.getName());
        }
    }

    public void test_Does_send_message_to_global_receiver() throws Exception {
        List<ServerEventMessage> msgs1 = new ArrayList<>();

        try(ServerEventsClient client1 = new ServerEventsClient("http://chat.servicestack.net")
                .registerReceiver(TestGlobalReceiver.class)
                .setOnMessage(msgs1::add)
                .start()
                .waitTillConnected()) {

            postObject(client1, new CustomType()
                .setId(1)
                .setName("Foo"));

            while (msgs1.size() < 1){
                Thread.sleep(100);
            }

            CustomType foo = TestGlobalReceiver.FooMethodReceived;
            assertNotNull(foo);
            assertEquals(1, foo.getId().intValue());
            assertEquals("Foo", foo.getName());
        }
    }

    public void test_Does_set_properties_on_global_receiver() throws Exception {
        List<ServerEventMessage> msgs1 = new ArrayList<>();

        try(ServerEventsClient client1 = new ServerEventsClient("http://chat.servicestack.net")
                .registerReceiver(TestGlobalReceiver.class)
                .setOnMessage(msgs1::add)
                .start()
                .waitTillConnected()) {

            postObject(client1, new SetterType()
                .setId(1)
                .setName("Foo"));

            SetterType foo = TestGlobalReceiver.AnyNamedSetterReceived;
            assertNotNull(foo);
            assertEquals(1, foo.getId().intValue());
            assertEquals("Foo", foo.getName());
        }
    }

    public void test_Does_send_raw_string_messages() throws Exception {
        List<ServerEventMessage> msgs1 = new ArrayList<>();

        try(ServerEventsClient client1 = new ServerEventsClient("http://chat.servicestack.net")
                .registerReceiver(TestJavaScriptReceiver.class)
                .registerNamedReceiver("css", TestJavaScriptReceiver.class)
                .setOnMessage(msgs1::add)
                .start()
                .waitTillConnected()) {

            postChat(client1, "chat msg");

            while (msgs1.size() < 1) {
                Thread.sleep(100);
            }

            ChatMessage chatMsg = TestJavaScriptReceiver.ChatReceived;
            assertNotNull(chatMsg);
            assertEquals("chat msg", chatMsg.getMessage());

            postRaw(client1, "cmd.announce", "This is your captain speaking...");

            while (msgs1.size() < 2) {
                Thread.sleep(100);
            }

            String announce = TestJavaScriptReceiver.AnnounceReceived;
            assertEquals("This is your captain speaking...", announce);

            postRaw(client1, "cmd.toggle$#channels", null);

            while (msgs1.size() < 3) {
                Thread.sleep(100);
            }

            String toggle = TestJavaScriptReceiver.ToggleReceived;
            assertEquals("", toggle);
            ServerEventMessage toggleRequest = TestJavaScriptReceiver.ToggleRequestReceived;
            assertEquals("cmd.toggle$#channels", toggleRequest.getSelector());
            assertEquals("cmd", toggleRequest.getOp());
            assertEquals("toggle", toggleRequest.getTarget());
            assertEquals("#channels", toggleRequest.getCssSelector());

            postRaw(client1, "css.background-image$#top", "url(http://bit.ly/1yIJOBH)");

            while (msgs1.size() < 4) {
                Thread.sleep(100);
            }

            String bgImage = TestJavaScriptReceiver.BackgroundImageReceived;
            assertEquals("url(http://bit.ly/1yIJOBH)", bgImage);
            ServerEventMessage bgImageRequest = TestJavaScriptReceiver.BackgroundImageRequestReceived;
            assertEquals("css.background-image$#top", bgImageRequest.getSelector());
            assertEquals("css", bgImageRequest.getOp());
            assertEquals("background-image", bgImageRequest.getTarget());
            assertEquals("#top", bgImageRequest.getCssSelector());
        }
    }

    public void test_Can_reuse_same_instance() throws Exception {
        List<ServerEventMessage> msgs1 = new ArrayList<>();

        try(ServerEventsClient client1 = new ServerEventsClient("http://chat.servicestack.net")
                .registerReceiver(TestJavaScriptReceiver.class)
                .registerNamedReceiver("css", TestJavaScriptReceiver.class)
                .setResolver(new SingletonInstanceResolver())
                .setOnMessage(msgs1::add)
                .start()
                .waitTillConnected()) {

            postRaw(client1, "cmd.announce", "This is your captain speaking...");

            while (msgs1.size() < 1) {
                Thread.sleep(100);
            }

            TestJavaScriptReceiver instance = (TestJavaScriptReceiver)client1.getResolver().TryResolve(TestJavaScriptReceiver.class);
            assertEquals("This is your captain speaking...", instance.AnnounceInstance);

            postRaw(client1, "cmd.announce", "2nd Announcement");

            while (msgs1.size() < 2) {
                Thread.sleep(100);
            }

            assertEquals("2nd Announcement", instance.AnnounceInstance);
        }
    }

    public void test_Can_use_IOC_to_autowire_Receivers(){
        //No built-in IOC
    }

    
}
