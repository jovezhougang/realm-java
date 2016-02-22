/*
 * Copyright 2016 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm;

import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.Iterator;
import java.util.ListIterator;

import io.realm.entities.AllTypes;
import io.realm.entities.NonLatinFieldNames;
import io.realm.exceptions.RealmException;
import io.realm.internal.log.RealmLog;
import io.realm.rule.TestRealmConfigurationFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class RealmResultsIteratorTests {

    private static final int TEST_SIZE = 10;

    @Rule
    public final TestRealmConfigurationFactory configFactory = new TestRealmConfigurationFactory();
    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    private Realm realm;
    private RealmResults<AllTypes> results;

    @Before
    public void setup() {
        realm = Realm.getInstance(configFactory.createConfiguration());
        populateRealm(realm, TEST_SIZE);
        results = realm.allObjectsSorted(AllTypes.class, AllTypes.FIELD_LONG, Sort.ASCENDING);
    }

    @After
    public void tearDown() {
        if (realm != null) {
            realm.close();
        }
    }

    private void populateRealm(Realm realm, int objects) {
        realm.beginTransaction();
        realm.allObjects(AllTypes.class).clear();
        realm.allObjects(NonLatinFieldNames.class).clear();
        for (int i = 0; i < objects; i++) {
            AllTypes allTypes = realm.createObject(AllTypes.class);
            allTypes.setColumnBoolean((i % 3) == 0);
            allTypes.setColumnBinary(new byte[]{1, 2, 3});
            allTypes.setColumnDate(new Date());
            allTypes.setColumnDouble(3.1415);
            allTypes.setColumnFloat(1.234567f + i);
            allTypes.setColumnString("test data " + i);
            allTypes.setColumnLong(i);
        }
        realm.commitTransaction();
    }

    @Test
    public void iterator() {
        Iterator<AllTypes> it = results.iterator();
        int i = 0;
        while(it.hasNext()) {
            AllTypes item = it.next();
            assertEquals("Failed at index: " + i, i, item.getColumnLong());
            i++;
        }
    }

    @Test
    public void iterator_remove_beforeNext() {
        Iterator<AllTypes> it = results.iterator();
        realm.beginTransaction();

        thrown.expect(IllegalStateException.class);
        it.remove();
    }

    @Test
    public void iterator_remove_deletesObject() {
        Iterator<AllTypes> it = results.iterator();
        AllTypes obj = it.next();
        assertEquals(0, obj.getColumnLong());
        realm.beginTransaction();
        it.remove();
        assertFalse(obj.isValid());
    }

    @Test
    public void iterator_remove_calledTwice() {
        Iterator<AllTypes> it = results.iterator();
        it.next();
        realm.beginTransaction();
        it.remove();

        thrown.expect(IllegalStateException.class);
        it.remove();
    }

    @Test
    public void iterator_transactionBeforeNextItem() {
        Iterator<AllTypes> it = results.iterator();
        int i = 0;
        while(it.hasNext()) {
            AllTypes item = it.next();
            assertEquals("Failed at index: " + i, i, item.getColumnLong());
            i++;

            // Committing transactions while iterating should not effect the current iterator.
            realm.beginTransaction();
            realm.createObject(AllTypes.class).setColumnLong(i - 1);
            realm.commitTransaction();
        }
    }

    @Test
    public void iterator_refreshWhileIterating() {
        Iterator<AllTypes> it = results.iterator();
        it.next();

        realm.beginTransaction();
        realm.createObject(AllTypes.class).setColumnLong(TEST_SIZE);
        realm.commitTransaction();
        realm.refresh(); // This will trigger rerunning all queries

        thrown.expect(ConcurrentModificationException.class);
        it.next();
    }

    @Test
    public void iterator_removedObjectsStillAccessible() {
        realm.beginTransaction();
        results.get(0).removeFromRealm();
        realm.commitTransaction();

        assertEquals(TEST_SIZE, results.size()); // Size is same even if object is deleted
        Iterator<AllTypes> it = results.iterator();
        AllTypes obj = it.next(); // Iterator can still access the deleted object
        RealmLog.d("ObjectId: " + obj.isValid());
        assertFalse(obj.isValid());
    }

    public void iterator_refreshClearsRemovedObjects() {
        realm.beginTransaction();
        results.where().equalTo(AllTypes.FIELD_LONG, 0).findFirst().removeFromRealm();
        realm.commitTransaction();

        // TODO How does refresh work with async queries?
        realm.refresh(); // Refresh forces a refresh of all RealmResults

        assertEquals(TEST_SIZE - 1, results.size()); // Size is same even if object is deleted
        Iterator<AllTypes> it = results.iterator();
        AllTypes types = it.next(); // Iterator can no longer access the deleted object

        assertTrue(types.isValid());
        assertEquals(1, types.getColumnLong());
    }

    @Test
    public void iterator_closedRealm_methodsThrows() {
        Iterator<AllTypes> it = results.iterator();
        realm.close();

        try {
            it.hasNext();
            fail();
        } catch (IllegalStateException ignored) {
        }

        try {
            it.next();
            fail();
        } catch (IllegalStateException ignored) {
        }

        try {
            it.remove();
            fail();
        } catch (IllegalStateException ignored) {
        }
    }

    @Test
    public void iterator_forEach() {
        int i = 0;
        for (AllTypes item : results) {
            assertEquals("Failed at index: " + i, i, item.getColumnLong());
            i++;
        }
    }

    @Test
    public void simple_iterator() {
        for (int i = 0; i < results.size(); i++) {
            assertEquals("Failed at index: " + i, i, results.get(i).getColumnLong());
        }
    }

    public void simple_iterator_transactionBeforeNextItem() {
        for (int i = 0; i < results.size(); i++) {
            // Committing transactions while iterating should not effect the current iterator.
            realm.beginTransaction();
            realm.createObject(AllTypes.class).setColumnLong(i);
            realm.commitTransaction();

            assertEquals("Failed at index: " + i, i, results.get(i).getColumnLong());
        }
    }

    @Test
    public void listIterator() {
        ListIterator<AllTypes> it = results.listIterator();

        // Test beginning of the list
        assertFalse(it.hasPrevious());
        assertTrue(it.hasNext());
        assertEquals(0, it.nextIndex());
        AllTypes firstObject = it.next();
        assertEquals(0, firstObject.getColumnLong());
        assertFalse(it.hasPrevious());

        // Move to second last element
        for (int i = 1; i < TEST_SIZE - 1; i++) {
            it.next();

        }

        // Test end of the list
        assertTrue(it.hasPrevious());
        assertTrue(it.hasNext());
        assertEquals(TEST_SIZE - 1, it.nextIndex());
        AllTypes lastObject = it.next();
        assertEquals(TEST_SIZE - 1, lastObject.getColumnLong());
        assertFalse(it.hasNext());
        assertEquals(TEST_SIZE, it.nextIndex());
    }

    @Test
    public void listIterator_defaultStartIndex() {
        ListIterator<AllTypes> it1 = results.listIterator(0);
        ListIterator<AllTypes> it2 = results.listIterator();

        assertEquals(it1.previousIndex(), it2.previousIndex());
        assertEquals(it1.nextIndex(), it2.nextIndex());
    }

    @Test
    public void listIterator_startIndex() {
        int i = TEST_SIZE/2;
        ListIterator<AllTypes> it = results.listIterator(i);

        assertTrue(it.hasPrevious());
        assertTrue(it.hasNext());
        assertEquals(i - 1, it.previousIndex());
        assertEquals(i, it.nextIndex());
        AllTypes nextObject = it.next();
        assertEquals(i, nextObject.getColumnLong());
    }

    @Test
    public void listIterator_closedRealm_methods() {
        int location = TEST_SIZE / 2;
        ListIterator<AllTypes> it = results.listIterator(location);
        realm.close();

        // These methods work even if the Realm is closed
        assertEquals(location - 1, it.previousIndex());
        assertEquals(location, it.nextIndex());

        // These methods will throw exceptions
        try {
            it.hasNext();
            fail();
        } catch (IllegalStateException ignored) {
        }

        try {
            it.next();
            fail();
        } catch (IllegalStateException ignored) {
        }

        try {
            it.previous();
            fail();
        } catch (IllegalStateException ignored) {
        }

        try {
            it.remove();
            fail();
        } catch (IllegalStateException ignored) {
        }

    }

    @Test(expected = RealmException.class)
    public void listIterator_set_thows() {
        results.listIterator().set(null);
    }

    @Test(expected = RealmException.class)
    public void listIterator_add_thows() {
        results.listIterator().set(null);
    }

    @Test
    public void listIterator_remove_beforeNext() {
        Iterator<AllTypes> it = results.listIterator();
        realm.beginTransaction();

        thrown.expect(IllegalStateException.class);
        it.remove();
    }

    @Test
    public void listIterator_remove_deletesObject() {
        Iterator<AllTypes> it = results.listIterator();
        AllTypes obj = it.next();
        assertEquals(0, obj.getColumnLong());
        realm.beginTransaction();
        it.remove();
        assertFalse(obj.isValid());
    }

    @Test
    public void listIterator_remove_calledTwice() {
        Iterator<AllTypes> it = results.listIterator();
        it.next();
        realm.beginTransaction();
        it.remove();

        thrown.expect(IllegalStateException.class);
        it.remove();
    }

    @Test
    public void listIterator_transactionBeforeNextItem() {
        Iterator<AllTypes> it = results.listIterator();
        int i = 0;
        while(it.hasNext()) {
            AllTypes item = it.next();
            assertEquals("Failed at index: " + i, i, item.getColumnLong());
            i++;

            // Committing transactions while iterating should not effect the current iterator.
            realm.beginTransaction();
            realm.createObject(AllTypes.class).setColumnLong(i - 1);
            realm.commitTransaction();
        }
    }

    @Test
    public void listIterator_refreshWhileIterating() {
        Iterator<AllTypes> it = results.listIterator();
        it.next();

        realm.beginTransaction();
        realm.createObject(AllTypes.class).setColumnLong(TEST_SIZE);
        realm.commitTransaction();
        realm.refresh(); // This will trigger rerunning all queries

        thrown.expect(ConcurrentModificationException.class);
        it.next();
    }

    @Test
    public void listIterator_removedObjectsStillAccessible() {
        realm.beginTransaction();
        results.where().equalTo(AllTypes.FIELD_LONG, 0).findFirst().removeFromRealm();
        realm.commitTransaction();

        assertEquals(TEST_SIZE, results.size()); // Size is same even if object is deleted
        Iterator<AllTypes> it = results.listIterator();
        AllTypes types = it.next(); // Iterator can still access the deleted object

        assertFalse(types.isValid());
    }

    public void listIterator_refreshClearsRemovedObjects() {
        realm.beginTransaction();
        results.where().equalTo(AllTypes.FIELD_LONG, 0).findFirst().removeFromRealm();
        realm.commitTransaction();

        // TODO How does refresh work with async queries?
        realm.refresh(); // Refresh forces a refresh of all RealmResults

        assertEquals(TEST_SIZE - 1, results.size()); // Size is same even if object is deleted
        Iterator<AllTypes> it = results.listIterator();
        AllTypes types = it.next(); // Iterator can no longer access the deleted object

        assertTrue(types.isValid());
        assertEquals(1, types.getColumnLong());
    }
}
