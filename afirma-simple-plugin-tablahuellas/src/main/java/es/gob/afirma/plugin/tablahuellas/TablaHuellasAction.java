/* 
 * 
 * InternallyDetachedFolder (Plugin): 
 * 
 * Plugin para calcular la huella digital de documentos y copiar el resultado al portapapeles
 * 
 * El código fuente de AutoFirma se encuentra disponible desde el repositorio público de GitHub:
 * 
 *    https://github.com/ctt-gob-es/clienteafirma
 * 
 * TablaHuellas (Plugin) puede redistribuirse y/o modificarse bajo los términos:
 * 
 *   - GNU General Public License versión 2 (GPLv2) o superior 
 *   
 * y se encuentra disponible desde el repositorio público de GitHub:
 * 
 *    https://github.com/digitaliza-aapp/afirma-plugins
 *   
 */

package es.gob.afirma.plugin.tablahuellas;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.Dialog.ModalityType;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.border.TitledBorder;

import es.gob.afirma.core.AOCancelledOperationException;
import es.gob.afirma.core.misc.AOUtil;
import es.gob.afirma.core.misc.Base64;
import es.gob.afirma.core.ui.AOUIFactory;
import es.gob.afirma.standalone.plugins.DataProcessAction;
import es.gob.afirma.standalone.plugins.InputData;

/**
 * Acción de validación para extraer documentos almacenados en archivos de firma
 * 
 * @author Felipe Muñoz Brieva
 * 
 */
public class TablaHuellasAction extends DataProcessAction {

	static final Logger LOGGER = Logger.getLogger(TablaHuellasAction.class.getName());

	private JDialog dialogoEntrada = null;

	private final JPanel panelSeleccionar = new JPanel();
	private final JPanel panelFormatoHuella = new JPanel();
	private final JPanel panelCopiar = new JPanel();
	private final JPanel panelSalir = new JPanel();

	private final TitledBorder borderCopiar = new TitledBorder(Messages.getString("FolderActionTexto.3"));
	private final TitledBorder borderSeleccionar = new TitledBorder(Messages.getString("FolderActionTexto.4"));
	private final TitledBorder borderFormatoHuella = new TitledBorder(Messages.getString("FolderActionTexto.20"));

	// *
	private JPanel documentosPanel = new JPanel();
	private JButton botonAnadirFichero = new JButton(Messages.getString("FolderActionTexto.7"));
	private JButton botonAnadirDirectorio = new JButton(Messages.getString("FolderActionTexto.18"));
	private JButton botonBorrarFichero = new JButton(Messages.getString("FolderActionTexto.17"));
	private JButton botonBorrarTodo = new JButton(Messages.getString("FolderActionTexto.19"));
	private final JCheckBox checkBoxSubdirectorio = new JCheckBox(Messages.getString("FolderActionTexto.13"));
	private final JCheckBox checkBoxSoloPdf = new JCheckBox(Messages.getString("FolderActionTexto.14"));
	private final JCheckBox checkBoxCrearHexhash = new JCheckBox(Messages.getString("FolderActionTexto.24"));
	private final JCheckBox checkBoxIncluirTamano = new JCheckBox(Messages.getString("FolderActionTexto.15"));
	private final JCheckBox checkBoxIncluirCarpeta = new JCheckBox(Messages.getString("FolderActionTexto.16"));

	private final static JTextField seleccionarDocumento = new JTextField();

	private DefaultListModel<String> documentosModelo = new DefaultListModel<>();
	private JList<String> documentosLista = new JList<>(documentosModelo);

	private DefaultListModel<String> documentosModeloSoloFicheros = new DefaultListModel<>();

	// *
	private final JLabel etiquetaAlgoritmo = new JLabel(Messages.getString("FolderActionTexto.21"));
	private final JLabel etiquetaFormato = new JLabel(Messages.getString("FolderActionTexto.22"));
	private final JLabel etiquetaFuente = new JLabel(Messages.getString("FolderActionTexto.23"));
	private final JLabel etiquetaPresentacion = new JLabel(Messages.getString("FolderActionTexto.10"));

	private final JComboBox<String> comboAlgoritmo = new JComboBox<>(Propiedades.HUELLA_ALGORITMOS);
	private final JComboBox<String> comboFormato = new JComboBox<>();
	private final JComboBox<String> comboFuente = new JComboBox<>();

	private ImageIcon icon = new ImageIcon(
			getClass().getResource("/es/gob/afirma/plugin/tablahuellas/TablaHuellas.png"));
	private JButton botonPortapapeles = new JButton(Messages.getString("FolderActionTexto.0"));

	// *
	private JButton botonSalir = new JButton(Messages.getString("FolderActionTexto.1"));

	private String defaultFont = "";

	// *
	boolean crearTabla = false;

	JDialog waitDialog = null;

	public TablaHuellasAction() {

		createUI();
	}

	@Override
	public void processData(InputData[] data, Window parent) {

		this.dialogoEntrada.setResizable(false);
		this.dialogoEntrada.setSize(650, 600);
		this.dialogoEntrada.setLocationRelativeTo(parent);
		this.dialogoEntrada.setVisible(true);

		if (crearTabla) {

			final SwingWorker<?, ?> validationWorker = crearTablaRtf();
			showWaitDialog(parent, validationWorker);
			AOUIFactory.showMessageDialog(parent, Messages.getString("FolderActionTexto.6"),
					Messages.getString("FolderActionTexto.5"), JOptionPane.PLAIN_MESSAGE);

		}

	}

	private void createUI() {

		// Obtener default font
		Font font = new JLabel().getFont();
		defaultFont = font.getFamily();

		GraphicsEnvironment graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment();
		Font[] allFonts = graphicsEnvironment.getAllFonts();

		Set<String> set = new TreeSet<>();

		// use for loop to pull the elements of array to hashmap's key
		for (int j = 0; j < allFonts.length; j++) {
			set.add(allFonts[j].getFamily());
		}

		// Combo Formato
		for (int i = 0; i < Propiedades.HUELLA_FORMATOS.length; i++) {

			comboFormato.addItem(Propiedades.HUELLA_FORMATOS[i][0]);
		}

		// print the set
		Iterator<String> iterator = set.iterator();
		while (iterator.hasNext()) {
			comboFuente.addItem(iterator.next().toString());
		}

		comboFuente.setSelectedItem(defaultFont);
		documentosLista.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		documentosLista.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

		documentosPanel.setLayout(new BorderLayout());
		documentosPanel.add(new JScrollPane(documentosLista));

		// JTextField para seleccionar documento
		seleccionarDocumento.setEditable(false);
		seleccionarDocumento.setFocusable(false);
		seleccionarDocumento.setColumns(10);

		// Botón para añadir documento
		botonAnadirFichero.setEnabled(true);

		botonAnadirFichero.addActionListener(e -> {
			try {

				if (checkBoxSoloPdf.isSelected()) {
					seleccionarDocumento.setText(AOUIFactory.getLoadFiles(Messages.getString("FolderActionTexto.7"),
							null, null, new String[] { "pdf" }, Messages.getString("FolderActionTexto.8"), false, true,
							null, null)[0].getAbsolutePath());
				} else {
					seleccionarDocumento.setText(AOUIFactory.getLoadFiles(Messages.getString("FolderActionTexto.7"),
							null, null, null, Messages.getString("FolderActionTexto.8"), false, true, null, null)[0]
									.getAbsolutePath());
				}

				documentosModelo.addElement(seleccionarDocumento.getText());

			} catch (final AOCancelledOperationException ex) {
				// Operacion cancelada por el usuario
			}
		});

		// Botón para añadir todos los documentos de un directorio
		botonAnadirDirectorio.setEnabled(true);

		botonAnadirDirectorio.addActionListener(e -> {
			try {

				seleccionarDocumento.setText(AOUIFactory.getLoadFiles(Messages.getString("FolderActionTexto.7"), null,
						null, null, Messages.getString("FolderActionTexto.8"), true, true, null, null)[0]
								.getAbsolutePath());

				documentosModelo.addElement(seleccionarDocumento.getText());

			} catch (final AOCancelledOperationException ex) {
				// Operacion cancelada por el usuario
			}
		});

		// Botón para borrar documento de la seleccion
		botonBorrarFichero.setEnabled(true);

		botonBorrarFichero.addActionListener(e -> {
			try {

				ListSelectionModel selmodel = documentosLista.getSelectionModel();
				int index = selmodel.getMinSelectionIndex();
				if (index >= 0)
					documentosModelo.remove(index);

			} catch (final AOCancelledOperationException ex) {
				// Operacion cancelada por el usuario
			}
		});

		// Botón para borrar todos los documentos de la seleccion
		botonBorrarTodo.setEnabled(true);

		botonBorrarTodo.addActionListener(e -> {
			try {
				documentosModelo.removeAllElements();
			} catch (final AOCancelledOperationException ex) {
				// Operacion cancelada por el usuario
			}
		});

		// Combo para seleccionar el algoritmo de Huella Digital
		comboAlgoritmo.setSelectedIndex(1);
		comboAlgoritmo.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				// JOptionPane.showMessageDialog(null,
				// comboAlgoritmo.getSelectedItem().toString());
			}
		});

		// Combo para seleccionar el formato de Huella Digital
		comboFormato.setSelectedIndex(1);
		comboFormato.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				// JOptionPane.showMessageDialog(null,
				// comboFormato.getSelectedItem().toString());
			}
		});

		// Boton Copiar huellas digitales al portapapeles
		botonPortapapeles.setEnabled(true);

		botonPortapapeles.setPreferredSize(new Dimension(350, 50));

		botonPortapapeles.setBackground(new Color(209, 209, 209));

		int width = (int) Math.round(icon.getIconWidth() / 1.8);
		int height = (int) Math.round(icon.getIconHeight() / 1.8);

		Image img = icon.getImage();
		Image resizedIcon = img.getScaledInstance(width, height, java.awt.Image.SCALE_SMOOTH);

		botonPortapapeles.setIcon(new ImageIcon(resizedIcon));

		botonPortapapeles.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				dialogoEntrada.dispose();

				crearTabla = true;
			}
		});

		// Boton Salir
		botonSalir.setEnabled(true);
		botonSalir.setPreferredSize(new Dimension(80, 50));

		botonSalir.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				documentosModelo.removeAllElements();
				dialogoEntrada.dispose();
				crearTabla = false;

			}
		});

		// Checkbox para leer todos los subdirectorios
		checkBoxSubdirectorio.setSelected(true);

		// Checkbox para seleccionar solo pdf
		checkBoxSoloPdf.setSelected(true);

		// Checkbox para controlar si deben crearse ficheros hexhash
		checkBoxCrearHexhash.setSelected(false);

		// Checkbox para controlar si debe incluirse el tamaño en la tabla
		checkBoxIncluirTamano.setSelected(false);

		// Checkbox para controlar si debe incluirse la carpeta en la tabla
		checkBoxIncluirCarpeta.setSelected(true);

		// PANELES -----------------------------------------------------------------

		// Panel Seleccionar -------------------------------------------------------
		borderSeleccionar.setTitleJustification(TitledBorder.LEFT);
		borderSeleccionar.setTitlePosition(TitledBorder.TOP);

		panelSeleccionar.setBorder(borderSeleccionar);
		panelSeleccionar.setLayout(new GridBagLayout());
		GridBagConstraints seleccionarGBC = new GridBagConstraints();

		seleccionarGBC.gridx = 0;
		seleccionarGBC.gridy = 0;
		seleccionarGBC.gridwidth = 3;
		seleccionarGBC.gridheight = 3;
		seleccionarGBC.weightx = 1.0;
		seleccionarGBC.weighty = 1.0;
		seleccionarGBC.fill = GridBagConstraints.BOTH;
		panelSeleccionar.add(documentosPanel, seleccionarGBC);

		seleccionarGBC.gridx = 3;
		seleccionarGBC.gridy = 0;
		seleccionarGBC.gridwidth = 1;
		seleccionarGBC.gridheight = 1;
		seleccionarGBC.fill = GridBagConstraints.HORIZONTAL;
		panelSeleccionar.add(botonAnadirFichero, seleccionarGBC);

		seleccionarGBC.gridx = 3;
		seleccionarGBC.gridy = 1;
		seleccionarGBC.gridwidth = 1;
		seleccionarGBC.gridheight = 1;
		panelSeleccionar.add(botonBorrarFichero, seleccionarGBC);

		GridBagConstraints seleccionarGBC1 = new GridBagConstraints();
		seleccionarGBC1.gridx = 3;
		seleccionarGBC1.gridy = 2;
		seleccionarGBC1.gridwidth = 1;
		seleccionarGBC1.gridheight = 1;
		seleccionarGBC1.anchor = GridBagConstraints.SOUTH;
		seleccionarGBC1.fill = GridBagConstraints.HORIZONTAL;
		seleccionarGBC1.insets = new Insets(0, 0, 100, 0);
		panelSeleccionar.add(botonBorrarTodo, seleccionarGBC1);

		// seleccionarGBC.insets = new Insets(15, 10, 5, 10);
		seleccionarGBC.gridx = 0;
		seleccionarGBC.gridy = 3;
		seleccionarGBC.gridwidth = 1;
		seleccionarGBC.gridheight = 1;
		panelSeleccionar.add(botonAnadirDirectorio, seleccionarGBC);

		seleccionarGBC.gridx = 1;
		seleccionarGBC.gridy = 3;
		seleccionarGBC.gridwidth = 1;
		seleccionarGBC.gridheight = 1;
		panelSeleccionar.add(checkBoxSubdirectorio, seleccionarGBC);

		seleccionarGBC.gridx = 2;
		seleccionarGBC.gridy = 3;
		seleccionarGBC.gridwidth = 1;
		seleccionarGBC.gridheight = 1;
		panelSeleccionar.add(checkBoxSoloPdf, seleccionarGBC);

		// Panel Formato Huella -----------------------------------------------

		borderFormatoHuella.setTitleJustification(TitledBorder.LEFT);
		borderFormatoHuella.setTitlePosition(TitledBorder.TOP);

		panelFormatoHuella.setBorder(borderFormatoHuella);
		panelFormatoHuella.setLayout(new FlowLayout(FlowLayout.CENTER));

		panelFormatoHuella.add(etiquetaAlgoritmo);
		panelFormatoHuella.add(comboAlgoritmo);

		panelFormatoHuella.add(etiquetaFormato);
		panelFormatoHuella.add(comboFormato);

		panelFormatoHuella.add(checkBoxCrearHexhash);

		// Panel Copiar ------------------------------------------------------------
		borderCopiar.setTitleJustification(TitledBorder.LEFT);
		borderCopiar.setTitlePosition(TitledBorder.TOP);

		panelCopiar.setBorder(borderCopiar);
		panelCopiar.setLayout(new FlowLayout(FlowLayout.CENTER));

		panelCopiar.add(etiquetaFuente);
		panelCopiar.add(comboFuente);
		panelCopiar.add(etiquetaPresentacion);
		panelCopiar.add(checkBoxIncluirTamano);
		panelCopiar.add(checkBoxIncluirCarpeta);

		panelSalir.add(botonPortapapeles);
		panelSalir.add(botonSalir);

		dialogoEntrada = new JDialog(null, Messages.getString("FolderActionTexto.2"), ModalityType.APPLICATION_MODAL);

		final Container container = this.dialogoEntrada.getContentPane();
		final GridBagLayout containerGBL = new GridBagLayout();
		final GridBagConstraints containerGBC = new GridBagConstraints();

		container.setLayout(containerGBL);

		this.dialogoEntrada.setLocationRelativeTo(null);

		// Añadir elementos a dialogoEntrada
		containerGBC.gridx = 0;
		containerGBC.gridy = 0;
		containerGBC.fill = GridBagConstraints.HORIZONTAL;
		containerGBC.weightx = 1.0;
		containerGBC.insets = new Insets(15, 10, 5, 10);

		container.add(panelSeleccionar, containerGBC);

		containerGBC.insets = new Insets(15, 10, 5, 10);
		containerGBC.gridx = 0;
		containerGBC.gridy++;
		container.add(panelFormatoHuella, containerGBC);

		containerGBC.insets = new Insets(15, 10, 5, 10);
		containerGBC.gridx = 0;
		containerGBC.gridy++;
		container.add(panelCopiar, containerGBC);

		containerGBC.insets = new Insets(15, 10, 5, 10);
		containerGBC.gridx = 0;
		containerGBC.gridy++;
		containerGBC.gridwidth = GridBagConstraints.REMAINDER;
		container.add(panelSalir, containerGBC);

	}

	public List<File> listarFicheros(String directorioNombre) {

		File directorio = new File(directorioNombre);

		List<File> resultList = new ArrayList<File>();

		File[] listadoFicheros = directorio.listFiles();
		resultList.addAll(Arrays.asList(listadoFicheros));

		for (File fichero : listadoFicheros) {
			if (fichero.isFile()) {
				documentosModeloSoloFicheros.addElement(fichero.getAbsolutePath().toString());
			} else if (fichero.isDirectory()) {

				// Deben incluirse los subdirectorios?
				if (checkBoxSubdirectorio.isSelected()) {
					resultList.addAll(listarFicheros(fichero.getAbsolutePath()));
				}
			}
		}

		return resultList;

	}

	// Convertir string a RTF
	public static String stringToRtf(String s) {
		StringBuilder stringCodificado = new StringBuilder();

		for (char ch : s.toCharArray()) {
			stringCodificado.append(charToRtf(ch));
		}

		return stringCodificado.toString();
	}

	// Convertir char a RTF
	public static String charToRtf(char c) {
		int intCode = c;

		String charToHex = Integer.toHexString(c);

		if (Character.isLetter(c) && intCode < 128) {
			return String.valueOf(c);
		}

		return "\\'" + charToHex;

	}

	public void crearHexhash(String fichero, String valorHuella) {

		File ficheroHexhash = new File(fichero);

		try {

			BufferedWriter bw = new BufferedWriter(new FileWriter(ficheroHexhash));
			bw.write(valorHuella);
			bw.close();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void copiarTablaHtml() {

		int sizeDocumentosModelo = documentosModelo.getSize();

		documentosModeloSoloFicheros.clear();

		for (int i = 0; i < sizeDocumentosModelo; i++) {

			Path path = Paths.get(documentosModelo.getElementAt(i).toString());

			if (Files.isRegularFile(path)) {
				documentosModeloSoloFicheros.addElement(documentosModelo.getElementAt(i).toString());
			}

			if (Files.isDirectory(path)) {
				listarFicheros(documentosModelo.getElementAt(i).toString());
			}

		}

		try {

			String rtfText = Propiedades.getString(Propiedades.PROP_RTF_HEADER).replace(
					Propiedades.getString(Propiedades.PROP_RTF_CAMPO_RTF_DEFAULT_FONT),
					comboFuente.getSelectedItem().toString())
					+ Propiedades.getString(Propiedades.PROP_RTF_TABLA_TITULO)
							.replace(Propiedades.getString(Propiedades.PROP_RTF_CAMPO_TITULO_CONTENIDO),
									Propiedades.getString(Propiedades.PROP_TABLA_TITULO))
							.replace(Propiedades.getString(Propiedades.PROP_RTF_CAMPO_TITULO_HUELLA_FORMATO),
									Propiedades.getString(Propiedades.PROP_FORMATO_HUELLA));

			int sizeDocumentosModeloSoloFicheros = documentosModeloSoloFicheros.getSize();

			for (int i = 0; i < sizeDocumentosModeloSoloFicheros; i++) {

				Path path = Paths.get(documentosModeloSoloFicheros.getElementAt(i).toString());

				// Excluir ficheros de hash
				if (path.getFileName().toString().endsWith(Propiedades.HUELLA_FORMATOS[0][1])
						|| path.getFileName().toString().endsWith(Propiedades.HUELLA_FORMATOS[1][1])
						|| path.getFileName().toString().endsWith(Propiedades.HUELLA_FORMATOS[2][1])) {
					continue;
				}

				// Solamente ficheros PDF
				if (this.checkBoxSoloPdf.isSelected() && !path.getFileName().toString()
						.endsWith(Propiedades.getString(Propiedades.PROP_EXTENSION_PDF))) {
					continue;
				}

				FileInputStream inputStream = new FileInputStream(
						new File(documentosModeloSoloFicheros.getElementAt(i).toString()));

				long bytes = Files.size(path);
				String bytesFormat = String.format("%,d bytes", bytes);

				String carpeta = path.getParent().toString().split(":")[1];

				byte[] data = AOUtil.getDataFromInputStream(inputStream);
				byte[] hash = MessageDigest.getInstance(comboAlgoritmo.getSelectedItem().toString()).digest(data);

				String valorHuella = "";

				if (comboFormato.getSelectedItem().toString().equals(Propiedades.HUELLA_FORMATOS[0][0])) {
					// 0 -> Hexadecimal
					valorHuella = AOUtil.hexify(hash, false) + "h";
				} else if (comboFormato.getSelectedItem().toString().equals(Propiedades.HUELLA_FORMATOS[1][0])) {
					// 1 -> Base64
					valorHuella = Base64.encode(hash);
				} else if (comboFormato.getSelectedItem().toString().equals(Propiedades.HUELLA_FORMATOS[2][0])) {
					valorHuella = "No definido";
				}

				rtfText = rtfText + Propiedades.getString(Propiedades.PROP_RTF_TABLA_FICHERO)
						.replace(Propiedades.getString(Propiedades.PROP_RTF_CAMPO_FICHERO_TITULO),
								Propiedades.getString(Propiedades.PROP_TABLA_FICHERO))
						.replace(Propiedades.getString(Propiedades.PROP_RTF_CAMPO_FICHERO_CONTENIDO),
								stringToRtf(path.getFileName().toString()));

				if (this.checkBoxIncluirCarpeta.isSelected()) {
					rtfText = rtfText + Propiedades.getString(Propiedades.PROP_RTF_TABLA_CARPETA)
							.replace(Propiedades.getString(Propiedades.PROP_RTF_CAMPO_CARPETA_TITULO),
									stringToRtf(Propiedades.getString(Propiedades.PROP_TABLA_CARPETA)))
							.replace(Propiedades.getString(Propiedades.PROP_RTF_CAMPO_CARPETA_CONTENIDO),
									stringToRtf(carpeta));
				}

				if (this.checkBoxIncluirTamano.isSelected()) {
					rtfText = rtfText + Propiedades.getString(Propiedades.PROP_RTF_TABLA_TAMANO)
							.replace(Propiedades.getString(Propiedades.PROP_RTF_CAMPO_TAMANO_TITULO),
									stringToRtf(Propiedades.getString(Propiedades.PROP_TABLA_TAMANO)))
							.replace(Propiedades.getString(Propiedades.PROP_RTF_CAMPO_TAMANO_CONTENIDO), bytesFormat);
				}

				rtfText = rtfText + Propiedades.getString(Propiedades.PROP_RTF_TABLA_HUELLA)
						.replace(Propiedades.getString(Propiedades.PROP_RTF_CAMPO_HUELLA_TITULO),
								Propiedades.getString(Propiedades.PROP_TABLA_HUELLA))
						.replace(Propiedades.getString(Propiedades.PROP_RTF_CAMPO_HUELLA_CONTENIDO), valorHuella);

				if (checkBoxCrearHexhash.isSelected()) {
					crearHexhash(
							documentosModeloSoloFicheros.getElementAt(i).toString()
									+ Propiedades.HUELLA_FORMATOS[(int) comboFormato.getSelectedIndex()][1].toString(),
							valorHuella);

				}

			}

			rtfText = rtfText
					+ Propiedades.getString(Propiedades.PROP_RTF_TABLA_TITULO_HUELLA_FORMATO).replace(
							Propiedades.getString(Propiedades.PROP_RTF_CAMPO_TITULO_HUELLA_FORMATO),
							Propiedades.getString(Propiedades.PROP_FORMATO_HUELLA) + comboAlgoritmo.getSelectedItem().toString() + " - "
									+ comboFormato.getSelectedItem().toString())
					+ Propiedades.getString(Propiedades.PROP_RTF_TABLA_FOOTER);

			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			Transferable htmlTransferable = new RtfInputStreamTransferable(rtfText);
			clipboard.setContents(htmlTransferable, null);

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private SwingWorker<Void, Void> crearTablaRtf() {

		final SwingWorker<Void, Void> validationWorker = new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() throws Exception {

				// Buscar todos los documentos que deben ser añadidos en la tabla
				copiarTablaHtml();

				return null;
			}

			@Override
			protected void done() {

				hideWaitDialog();

			}

		};

		validationWorker.execute();
		return validationWorker;

	}

	private void showWaitDialog(Window parent, SwingWorker<?, ?> worker) {
		this.waitDialog = new JDialog(parent, Messages.getString("FolderActionTexto.3"),
				ModalityType.APPLICATION_MODAL);
		this.waitDialog.setLayout(new GridBagLayout());

		final JLabel textLabel = new JLabel(Messages.getString("FolderActionTexto.4"));
		textLabel.setMinimumSize(new Dimension(250, 15));
		final JProgressBar progressBar = new JProgressBar();
		progressBar.setIndeterminate(true);

		final GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.0;
		c.gridy = 0;
		c.insets = new Insets(11, 11, 0, 11);
		this.waitDialog.add(textLabel, c);
		c.gridy++;
		c.insets = new Insets(6, 11, 11, 11);
		this.waitDialog.add(progressBar, c);

		this.waitDialog.pack();
		this.waitDialog.setLocationRelativeTo(parent);
		this.waitDialog.setVisible(true);

		if (worker != null) {
			worker.cancel(true);
		}

	}

	void hideWaitDialog() {
		this.waitDialog.dispose();
	}

}
