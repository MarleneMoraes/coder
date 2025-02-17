package br.ce.wcaquino.servicos;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import br.ce.wcaquino.daos.LocacaoDAO;
import br.ce.wcaquino.entidades.Filme;
import br.ce.wcaquino.entidades.Locacao;
import br.ce.wcaquino.entidades.Usuario;
import br.ce.wcaquino.exceptions.FilmesSemEstoqueException;
import br.ce.wcaquino.exceptions.LocadoraException;
import br.ce.wcaquino.utils.DataUtils;

public class LocacaoService {
	
	private LocacaoDAO dao;
	private SPCService spc;
	private EmailService email;

	public Locacao alugarFilme(Usuario usuario, List<Filme> filmes) throws FilmesSemEstoqueException, LocadoraException {
		
		if(usuario == null) {
			throw new LocadoraException("Usuario vazio");
		}
		
		if(filmes == null || filmes.isEmpty()) {
			throw new LocadoraException("Filme vazio");
		}

		for(Filme filme : filmes) {
			if(filme.getEstoque() == 0) {
				throw new FilmesSemEstoqueException();
			}
		}
		
		boolean negativado;
		
		try {
			negativado = spc.eNegativado(usuario);
		} catch (Exception e) {
			throw new LocadoraException("SPC fora do ar, tente novamente.");
		}
		

		if(negativado) {
			throw new LocadoraException("Usuario Negativado.");
		}
		
		Locacao locacao = new Locacao();
		locacao.setFilmes(filmes);
		locacao.setUsuario(usuario);
		locacao.setDataLocacao(obterData());
		// locacao.setDataLocacao(Calendar.getInstance().getTime());
		
		locacao.setValor(calcularValorLocacao(filmes));

		// Entrega no dia seguinte
		// Date dataEntrega = Calendar.getInstance().getTime();
		Date dataEntrega = obterData();
		dataEntrega = DataUtils.adicionarDias(dataEntrega, 1);
		
		if(DataUtils.verificarDiaSemana(dataEntrega, Calendar.SUNDAY)) {
			dataEntrega = DataUtils.adicionarDias(dataEntrega, 2);
		}
		
		locacao.setDataRetorno(dataEntrega);
		
		dao.salvar(locacao);

		return locacao;
	}

	protected Date obterData() {
		return new Date();
	}

	private Double calcularValorLocacao(List<Filme> filmes) {
		Double precoTotal = 0.0;
		
		for(int i = 0; i < filmes.size(); i++) {
			Double valorFilme = filmes.get(i).getPrecoLocacao();
			
			switch(i) {
				case 2:
					valorFilme = valorFilme * 0.75;
					break;
				case 3:
					valorFilme = valorFilme * 0.5;
					break;
				case 4:
					valorFilme = valorFilme * 0.25;
					break;
				case 5:
					valorFilme = valorFilme * 0.0;
					break;	
				default:
					valorFilme = valorFilme * 1;
			}
			
			precoTotal += valorFilme;	
		}
		return precoTotal;
	}
	
	public void notificarAtrasos() {
		List<Locacao> locacoes = dao.obterLocacoesPendentes(); 
		
		for(Locacao locacao : locacoes) {
			if(locacao.getDataRetorno().before(obterData())) {
				email.notificarAtraso(locacao.getUsuario());				
			}
		}
	}
	
	public void prorrogarLocacao(Locacao locacao, int dias) {
		Locacao novaLocacao = new Locacao();
		
		novaLocacao.setUsuario(locacao.getUsuario());
		novaLocacao.setFilmes(locacao.getFilmes());
		novaLocacao.setDataLocacao(obterData());
		novaLocacao.setDataRetorno(DataUtils.obterDataComDiferencaDias(dias));
		novaLocacao.setValor(locacao.getValor() * dias);
		
		dao.salvar(novaLocacao);
	}

}