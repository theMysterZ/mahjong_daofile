package fr.univubs.inf1603.mahjong.daofile;

import fr.univubs.inf1603.mahjong.daofile.filemanagement.LinkManager;
import fr.univubs.inf1603.mahjong.daofile.filemanagement.DataRow;
import fr.univubs.inf1603.mahjong.daofile.filemanagement.DAOFileWriter;
import fr.univubs.inf1603.mahjong.dao.DAOException;
import fr.univubs.inf1603.mahjong.daofile.exception.DAOFileException;
import fr.univubs.inf1603.mahjong.daofile.exception.DAOFileWriterException;
import fr.univubs.inf1603.mahjong.engine.game.GameTileInterface;
import fr.univubs.inf1603.mahjong.engine.game.MahjongTileZone;
import fr.univubs.inf1603.mahjong.engine.game.TileZone;
import fr.univubs.inf1603.mahjong.engine.game.TileZoneIdentifier;
import java.beans.PropertyChangeEvent;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * La classe {@code FileZoneDAO} gère la persistance des zones {@code TileZone}
 * {@link FileZoneDAO.ZoneRow}.
 *
 * @author aliyou
 * @version 1.3
 */
public class FileZoneDAO extends FileDAOMahjong<TileZone> {

    public static final String ZONE_WRITED_PROPERTY = "zoneWrited";
    
    /**
     * Logging
     */
    private static final Logger LOGGER = Logger.getLogger(FileZoneDAO.class.getName());
    
    /**
     *  Contient l'Instance du DAO qui gère les zones.
     */
    private static FileZoneDAO instance;

    /**
     * DAO qui gère les tuiles
     */
    final private FileTileDAO tileDAO;  
    /**
     * Gestionnaire de liens entre les tuiles et les zones.
     */
    /*final*/ static private LinkManager<GameTileInterface> tileToZoneLinkManager;
    /**
     * Gestionnaire de liens entre les zones et les parties de Mahjong.
     */
    final private LinkManager<TileZone> zoneToGameLinkManager;
    
    /**
     * Constructeur privé avec un Chemin d'accès du répertoire racine
     * <code>rootDirPath</code>.
     *
     * @param rootDirPath Chemin d'accès du répertoire racine. NE DOIT PAS ETRE
     * NULL.
     * @throws DAOFileException s'il ya une erreur lors de l'instanciation.
     */
    private FileZoneDAO(Path rootDirPath) throws DAOFileException {
        super(rootDirPath, "zone", ZoneRow.ZONE_ROW_SIZE);
        this.zoneToGameLinkManager = new ZoneToGameLinkManager(this);
        this.tileDAO = FileTileDAO.getInstance(rootDirPath);
        this.tileDAO.addPropertyChangeListener(this);
        FileZoneDAO.tileToZoneLinkManager = this.tileDAO.getLinkManager();
    }
    
    @Override
    synchronized public void propertyChange(PropertyChangeEvent evt) {
        if(evt.getPropertyName().equals(FileTileDAO.TILE_WRITED_PROPERTY)) {
            System.out.println("* FileZoneGame -> job done notification received from FileTileDAO");
            notify();
            super.getPropertyChangeSupport().firePropertyChange(ZONE_WRITED_PROPERTY, false, true);
        }
    }
    
    /**
     * Renvoie l'instance du DAO qui gère les zones.
     * 
     * @param rootDir Chemin d'accès du répertoire racine. NE DOIT PAS ETRE
     * NULL.
     * @return L'instance du DAO qui gère les zones.
     * @throws DAOFileException s'il y'a une erreur lors de l'instanciation.
     */
    static FileZoneDAO getInstance(Path rootDir) throws DAOFileException {
        if(instance == null) {
            instance = new FileZoneDAO(rootDir);
        }
        return instance;
    }
    
    /**
     * @return Le gestionnaire de liens entre les zones et les parties de Mahjong.
     */
    LinkManager<TileZone> getLinkManager() {
        return zoneToGameLinkManager;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected DataRow getDataRow(int rowID, TileZone tileZone, long rowPointer) {
        return new ZoneRow(rowID, tileZone, rowPointer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected DataRow getDataRow(long pointer) throws DAOFileException {
        return new ZoneRow(dataWriter, pointer);
    }

    /**
     * Supprime une zone {@code TileZone} du fichier de données.
     *
     * @param tileZone Zone à supprimer.
     * @throws DAOException s'il y'a une erreur lors de la suppression.
     */
    @Override
    protected void deleteFromPersistence(TileZone tileZone) throws DAOException {
        try {
            if (zoneToGameLinkManager.getRow(tileZone.getUUID()) == null) {
                // suppression des tuiles qui sont dans la zone
                tileToZoneLinkManager.removeChildren(tileZone.getTiles());
                if (super.removeDataRow(tileZone.getUUID())) {
                    LOGGER.log(Level.INFO, "{0} id={1} deleted from persistance",
                            new Object[]{tileZone.getClass().getSimpleName(), tileZone.getUUID()});
                }
            } else {
                String message = "TileZone id=" + tileZone.getUUID() + " can't be deleted"
                        + "\n\t cause -> it's linked to a game.";
                LOGGER.log(Level.WARNING, message);
                throw new DAOException(message);
            }
        } catch (DAOFileException ex) {
            throw new DAOException(ex.getMessage(), ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(List<TileZone> zones) throws DAOFileException {
        LOGGER.log(Level.INFO, "** delete zoneList -> zones.size {0}", zones.size());
        try {
            for (TileZone tz : zones) {
                deleteFromPersistence(tz);
            }
        } catch (DAOException ex) {
            throw new DAOFileException(ex.getMessage(), ex);
        }
    }

    /**
     * Cette classe represente le gestionnaire de liens entre les zones et les 
     * parties de Mahjong.
     */
    class ZoneToGameLinkManager extends LinkManager<TileZone> {

        /**
         * Constructeur privé avec le DAO qui gère les zones.
         * @param dao DAO qui gère les zones.
         * @throws DAOFileException s'il y'a une erreur lors de l'instanciation.
         */
        ZoneToGameLinkManager(FileDAOMahjong<TileZone> dao) throws DAOFileException {
            super(rootDirPath.resolve("zoneToGame.link"), dao);
//            System.out.println("constructor : " + this);
        }

    }

    /**
     * La classe <code>ZoneRow</code> répresente un tuple de zone
     * {@link TileZone}. C'est un conteneur pour une zone.
     *
     * <pre>
     *
     *      Format d'une zone dans un tuple :
     *
     *          UUID=16    |    int=4    |      int=4       | String=16  |   -> (Byte) 40
     *         tileZoneID  |   nbTiles   | identifierLenght | identifier |
     *   ex :       -      |      -      |        -         |    WALL    |
     * </pre>
     *
     */
    static class ZoneRow extends DataRow<TileZone> {

        /**
         * Taille d'une zone en octet.
         */
        private static final int ZONE_SIZE = 16 + 4 + (4 + 16);             // 40
        /**
         * Taille d'un tuple de zone.
         */
        static final int ZONE_ROW_SIZE = ROW_HEADER_SIZE + ZONE_SIZE;       // 44

        /**
         * Constructeur avec l'identifiant d'un tuple <code>rowID</code>, une
         * zone <code>data</code> et un pointeur de tuple
         * <code>rowPointer</code>.
         *
         * @param rowID Identifiant du tuple.
         * @param data Zone encapsulé dans le tuple.
         * @param rowPointer Pointeur de tuple.
         */
        ZoneRow(int rowID, TileZone data, long rowPointer) {
            super(rowID, data, ZONE_SIZE, rowPointer);
        }

        /**
         * Constructeur avec un processus qui éffectue des opérations
         * d'entrée/sortie sur un fichier <code>writer</code> et un pointeur de
         * tuple <code>rowPointer</code>..
         *
         * @param writer Processus qui éffectue des opérations d'entrée/sortie
         * sur un fichier.
         * @param rowPointer Pointeur d'un tuple.
         * @throws DAOFileException s'il y'a une erruer lors de la lecture d'une
         * zone <code>TileZone</code>.
         */
        ZoneRow(DAOFileWriter writer, long rowPointer) throws DAOFileException {
            super(writer, ZONE_SIZE, rowPointer);
        }

        /**
         * Change l'état d'un tuple de zone lorsque une nouvelle tuile est
         * rajoutée à la zone.
         *
         * @param evt Evenement
         */
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (evt.getPropertyName().equals(TileZone.TILELIST)) {
                setDirty(true);
            }
        }

        /**
         * Renvoie une zone {@code TileZone} lue à partir d'un tampon d'octets
         * {@code buffer}.
         *
         * @param buffer Tampon d'octets à partir duquel une zone
         * <code>TileZone</code> est lue.
         */
        @Override
        protected TileZone readData(ByteBuffer buffer) throws DAOFileException {
            if (buffer.remaining() < ZONE_SIZE) {
                String message = "Remianing bytes '" + buffer.remaining() + "' is less than ZONE_SIZE '"
                        + ZONE_SIZE + "'";
                throw new DAOFileException(message);
            }
            try {
                UUID zoneID = new UUID(buffer.getLong(), buffer.getLong());
                int nbTiles = buffer.getInt();
                String ident = DAOFileWriter.readString(buffer);
                TileZoneIdentifier identifier = TileZoneIdentifier.valueOf(ident);
                ArrayList<GameTileInterface> tiles = new ArrayList<>();
                if (nbTiles != 0) {
                    try {
                        tiles = tileToZoneLinkManager.loadChildren(zoneID);
                    } catch (DAOException ex) {
                        throw new DAOFileException(ex.getMessage(), ex);
                    }
                }
                TileZone data = new MahjongTileZone(tiles, zoneID, identifier);
                return data;
            } catch (DAOFileWriterException ex) {
                throw new DAOFileException(ex.getMessage(), ex);
            }
        }

        /**
         * Ecrit une zone <code>TileZone</code> dans un tampon d'octet
         * <code>buffer</code>.
         *
         * @param buffer Tampon d'octet dans lequel la zone est écrite.
         */
        @Override
        protected int writeData(ByteBuffer buffer) throws DAOFileException {
            if (buffer.remaining() < ZONE_SIZE) {
                String message = "Remianing bytes '" + buffer.remaining() + "' is less than ZONE_SIZE '"
                        + ZONE_SIZE + "'";
                throw new DAOFileException(message);
            }
            try {
                int stratPosition = buffer.position();
                DAOFileWriter.writeUUID(buffer, getData().getUUID());
                int nbTiles = getData().getTiles().size();
                buffer.putInt(nbTiles);
                DAOFileWriter.writeString(buffer, getData().getIdentifier().toString());
                if (!isWritedInFile()) { // première écriture dans le fichier de données
                    if (nbTiles != 0) {
                        tileToZoneLinkManager.addLink(getData().getUUID(), getData().getTiles());
                    }
                    setWritedInFile(true);
                } else { // mis à jour
                    tileToZoneLinkManager.updateLink(getData().getUUID(), getData().getTiles());
                }
                return buffer.position() - stratPosition;
            } catch (DAOFileWriterException ex) {
                throw new DAOFileException(ex.getMessage(), ex);
            }
        }
    }
}
