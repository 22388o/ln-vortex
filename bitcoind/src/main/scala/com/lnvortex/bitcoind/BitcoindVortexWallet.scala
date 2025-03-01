package com.lnvortex.bitcoind

import akka.actor.ActorSystem
import com.lnvortex.core.api._
import com.lnvortex.core.{InputReference, UnspentCoin}
import org.bitcoins.commons.jsonmodels.bitcoind.RpcOpts.LockUnspentOutputParameter
import org.bitcoins.core.config.BitcoinNetwork
import org.bitcoins.core.currency.CurrencyUnit
import org.bitcoins.core.number.UInt32
import org.bitcoins.core.protocol.BitcoinAddress
import org.bitcoins.core.protocol.ln.node.NodeId
import org.bitcoins.core.protocol.script.ScriptWitness
import org.bitcoins.core.protocol.transaction._
import org.bitcoins.core.psbt.PSBT
import org.bitcoins.crypto.{DoubleSha256DigestBE, SchnorrNonce}
import org.bitcoins.rpc.client.common.BitcoindRpcClient
import scodec.bits.ByteVector

import java.net.InetSocketAddress
import scala.concurrent.Future

case class BitcoindVortexWallet(
    bitcoind: BitcoindRpcClient,
    walletNameOpt: Option[String] = None)(implicit system: ActorSystem)
    extends VortexWalletApi {
  import system.dispatcher

  override val network: BitcoinNetwork = bitcoind.instance.network match {
    case network: BitcoinNetwork => network
  }

  override def getNewAddress(): Future[BitcoinAddress] =
    bitcoind.getNewAddress(walletNameOpt)

  override def getChangeAddress(): Future[BitcoinAddress] = {
    walletNameOpt match {
      case Some(walletName) => bitcoind.getRawChangeAddress(walletName)
      case None             => bitcoind.getRawChangeAddress
    }
  }

  override def listCoins(): Future[Vector[UnspentCoin]] = {
    val utxosF = walletNameOpt match {
      case Some(walletName) => bitcoind.listUnspent(walletName)
      case None             => bitcoind.listUnspent
    }

    utxosF.map(_.map { utxo =>
      val outPoint = TransactionOutPoint(utxo.txid, UInt32(utxo.vout))
      UnspentCoin(utxo.address.get,
                  utxo.amount,
                  outPoint,
                  utxo.confirmations > 0)
    })
  }

  override def createInputProof(
      nonce: SchnorrNonce,
      outputRef: OutputReference): Future[ScriptWitness] = {
    val tx = InputReference.constructInputProofTx(outputRef, nonce)
    val psbt = PSBT.fromUnsignedTx(tx)

    for {
      signed <- bitcoind.walletProcessPSBT(psbt, walletNameOpt = walletNameOpt)

      param = LockUnspentOutputParameter(outputRef.outPoint.txIdBE,
                                         outputRef.outPoint.vout.toInt)
      _ <- bitcoind.lockUnspent(unlock = false, Vector(param))
    } yield signed.psbt.inputMaps.head.finalizedScriptWitnessOpt.get.scriptWitness
  }

  override def signPSBT(
      unsigned: PSBT,
      outputRefs: Vector[OutputReference]): Future[PSBT] = {
    bitcoind.walletProcessPSBT(unsigned, walletNameOpt = walletNameOpt).map {
      res =>
        val signed = res.psbt

        // make sure we signed correct inputs
        val signedIndexes = outputRefs.map { outputRef =>
          val index = signed.transaction.inputs.indexWhere(
            _.previousOutput == outputRef.outPoint)

          require(signed.inputMaps(index).isFinalized,
                  s"Did not correctly sign for input ${outputRef.outPoint}")
          index
        }
        // make sure we didn't sign extra inputs
        val extraSigs =
          signed.inputMaps.zipWithIndex.count { case (input, idx) =>
            if (!signedIndexes.contains(idx)) {
              !input.isFinalized && input.partialSignatures.isEmpty
            } else false // skip our inputs
          }

        require(extraSigs == 0,
                s"PSBT contained $extraSigs extra signed inputs")

        signed
    }
  }

  override def broadcastTransaction(transaction: Transaction): Future[Unit] =
    bitcoind.broadcastTransaction(transaction)

  override def labelTransaction(
      txId: DoubleSha256DigestBE,
      label: String): Future[Unit] =
    Future.unit // bitcoind doesn't have tx labeling

  override def initChannelOpen(
      nodeId: NodeId,
      peerAddrOpt: Option[InetSocketAddress],
      fundingAmount: CurrencyUnit,
      privateChannel: Boolean): Future[OutputDetails] =
    Future.failed(
      new UnsupportedOperationException("Bitcoind is not a lightning wallet"))

  override def completeChannelOpen(
      chanId: ByteVector,
      psbt: PSBT): Future[Unit] = Future.failed(
    new UnsupportedOperationException("Bitcoind is not a lightning wallet"))

  override def cancelChannel(
      chanOutPoint: TransactionOutPoint,
      nodeId: NodeId): Future[Unit] =
    Future.failed(
      new UnsupportedOperationException("Bitcoind is not a lightning wallet"))

  override def start(): Future[Unit] = bitcoind.start().map(_ => ())

  override def stop(): Future[Unit] = Future.unit

  override def listTransactions(): Future[Vector[TransactionDetails]] = {
    // todo
    Future.successful(Vector.empty)
  }

  override def listChannels(): Future[Vector[ChannelDetails]] = Future.failed(
    new UnsupportedOperationException("Bitcoind is not a lightning wallet"))

}
