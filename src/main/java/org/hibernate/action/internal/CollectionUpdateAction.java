package org.hibernate.action.internal;

import my.com.examples.hibernate5.dao.MyPostCollectionUpdateEvent;
import org.hibernate.AssertionFailure;
import org.hibernate.HibernateException;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.event.service.spi.EventListenerGroup;
import org.hibernate.event.spi.*;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.pretty.MessageHelper;

import java.io.Serializable;

public class CollectionUpdateAction extends CollectionAction {

    private final boolean emptySnapshot;
    private Object oldObject;

    /**
     * Constructs a CollectionUpdateAction
     *
     * @param collection The collection to update
     * @param persister The collection persister
     * @param id The collection key
     * @param emptySnapshot Indicates if the snapshot is empty
     * @param session The session
     */
    public CollectionUpdateAction(
            final PersistentCollection collection,
            final CollectionPersister persister,
            final Serializable id,
            final boolean emptySnapshot,
            final SessionImplementor session) {
        super( persister, collection, id, session );
        this.emptySnapshot = emptySnapshot;
    }

    @Override
    public void execute() throws HibernateException {
        final Serializable id = getKey();
        final SessionImplementor session = getSession();
        final CollectionPersister persister = getPersister();
        final PersistentCollection collection = getCollection();
        final boolean affectedByFilters = persister.isAffectedByEnabledFilters( session );

        preUpdate();
        this.oldObject = session.getPersistenceContext().getSnapshot(collection);
        
        if ( !collection.wasInitialized() ) {
            if ( !collection.hasQueuedOperations() ) {
                throw new AssertionFailure( "no queued adds" );
            }
            //do nothing - we only need to notify the cache... 
        }
        else if ( !affectedByFilters && collection.empty() ) {
            if ( !emptySnapshot ) {
                persister.remove( id, session );
            }
        }
        else if ( collection.needsRecreate( persister ) ) {
            if ( affectedByFilters ) {
                throw new HibernateException(
                        "cannot recreate collection while filter is enabled: " +
                                MessageHelper.collectionInfoString( persister, collection, id, session )
                );
            }
            if ( !emptySnapshot ) {
                persister.remove( id, session );
            }
            persister.recreate( collection, id, session );
        }
        else {
            persister.deleteRows( collection, id, session );
            persister.updateRows( collection, id, session );
            persister.insertRows( collection, id, session );
        }

        getSession().getPersistenceContext().getCollectionEntry( collection ).afterAction( collection );
        evict();
        postUpdate();

        if ( getSession().getFactory().getStatistics().isStatisticsEnabled() ) {
            getSession().getFactory().getStatisticsImplementor().updateCollection( getPersister().getRole() );
        }
    }

    private void preUpdate() {
        final EventListenerGroup<PreCollectionUpdateEventListener> listenerGroup = listenerGroup( EventType.PRE_COLLECTION_UPDATE );
        if ( listenerGroup.isEmpty() ) {
            return;
        }
        final PreCollectionUpdateEvent event = new PreCollectionUpdateEvent(
                getPersister(),
                getCollection(),
                eventSource()
        );
        for ( PreCollectionUpdateEventListener listener : listenerGroup.listeners() ) {
            listener.onPreUpdateCollection( event );
        }
    }

    private void postUpdate() {
        final EventListenerGroup<PostCollectionUpdateEventListener> listenerGroup = listenerGroup( EventType.POST_COLLECTION_UPDATE );
        if ( listenerGroup.isEmpty() ) {
            return;
        }
        final MyPostCollectionUpdateEvent event = new MyPostCollectionUpdateEvent(
                oldObject,
                getPersister(),
                getCollection(),
                eventSource()
        );
        for ( PostCollectionUpdateEventListener listener : listenerGroup.listeners() ) {
            listener.onPostUpdateCollection( event );
        }
    }
}
