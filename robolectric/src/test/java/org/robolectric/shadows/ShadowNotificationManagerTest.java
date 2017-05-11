package org.robolectric.shadows;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import android.content.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.TestRunners;
import org.robolectric.annotation.Config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.robolectric.Shadows.shadowOf;

@RunWith(TestRunners.MultiApiSelfTest.class)
public class ShadowNotificationManagerTest {
  private NotificationManager notificationManager;
  private Notification notification1 = new Notification();
  private Notification notification2 = new Notification();

  @Before public void setUp() {
    notificationManager = (NotificationManager) RuntimeEnvironment.application.getSystemService(Context.NOTIFICATION_SERVICE);
  }

  @Test
  @Config(minSdk = Build.VERSION_CODES.O)
  public void createNotificationChannel() {
    notificationManager.createNotificationChannel(new NotificationChannel("id", "name", 1));

    assertThat(shadowOf(notificationManager).getNotificationChannels()).hasSize(1);
    assertThat(shadowOf(notificationManager).getNotificationChannel("id").getName()).isEqualTo("name");
    assertThat(shadowOf(notificationManager).getNotificationChannel("id").getImportance()).isEqualTo(1);
  }

  @Test
  @Config(minSdk = Build.VERSION_CODES.O)
  public void createNotificationChannelGroup() {
    notificationManager.createNotificationChannelGroup(new NotificationChannelGroup("id", "name"));

    assertThat(shadowOf(notificationManager).getNotificationChannelGroups()).hasSize(1);
    assertThat(shadowOf(notificationManager).getNotificationChannelGroup("id").getName()).isEqualTo("name");
  }

  @Test
  public void testNotify() throws Exception {
    notificationManager.notify(1, notification1);
    assertEquals(1, shadowOf(notificationManager).size());
    assertEquals(notification1, shadowOf(notificationManager).getNotification(null, 1));

    notificationManager.notify(31, notification2);
    assertEquals(2, shadowOf(notificationManager).size());
    assertEquals(notification2, shadowOf(notificationManager).getNotification(null, 31));
  }

  @Test
  public void testNotifyReplaces() throws Exception {
    notificationManager.notify(1, notification1);

    notificationManager.notify(1, notification2);
    assertEquals(1, shadowOf(notificationManager).size());
    assertEquals(notification2, shadowOf(notificationManager).getNotification(null, 1));
  }

  @Test
  public void testNotifyWithTag() throws Exception {
    notificationManager.notify("a tag", 1, notification1);
    assertEquals(1, shadowOf(notificationManager).size());
    assertEquals(notification1, shadowOf(notificationManager).getNotification("a tag", 1));
  }

  @Test
  public void notifyWithTag_shouldReturnNullForNullTag() throws Exception {
    notificationManager.notify("a tag", 1, notification1);
    assertEquals(1, shadowOf(notificationManager).size());
    assertNull(shadowOf(notificationManager).getNotification(null, 1));
  }

  @Test
  public void notifyWithTag_shouldReturnNullForUnknownTag() throws Exception {
    notificationManager.notify("a tag", 1, notification1);
    assertEquals(1, shadowOf(notificationManager).size());
    assertNull(shadowOf(notificationManager).getNotification("unknown tag", 1));
  }

  @Test
  public void testCancel() throws Exception {
    notificationManager.notify(1, notification1);
    notificationManager.cancel(1);

    assertEquals(0, shadowOf(notificationManager).size());
    assertNull(shadowOf(notificationManager).getNotification(null, 1));
  }

  @Test
  public void testCancelWithTag() throws Exception {
    notificationManager.notify("a tag", 1, notification1);
    notificationManager.cancel("a tag", 1);

    assertEquals(0, shadowOf(notificationManager).size());
    assertNull(shadowOf(notificationManager).getNotification(null, 1));
    assertNull(shadowOf(notificationManager).getNotification("a tag", 1));
  }

  @Test
  public void testCancelAll() throws Exception {
    notificationManager.notify(1, notification1);
    notificationManager.notify(31, notification2);
    notificationManager.cancelAll();

    assertEquals(0, shadowOf(notificationManager).size());
    assertNull(shadowOf(notificationManager).getNotification(null, 1));
    assertNull(shadowOf(notificationManager).getNotification(null, 31));
  }

  @Test
  @Config(minSdk = Build.VERSION_CODES.M)
  public void testGetActiveNotifications() throws Exception {
    notificationManager.notify(1, notification1);
    notificationManager.notify(31, notification2);

    StatusBarNotification[] statusBarNotifications =
        shadowOf(notificationManager).getActiveNotifications();
    assertThat(statusBarNotifications)
        .extractingResultOf("getNotification", Notification.class)
        .containsOnly(notification1, notification2);
  }
}
